"""Authenticated LAN client. Never expose credentials in URLs or errors."""
import asyncio
from http.cookies import SimpleCookie
import re

from aiohttp import ClientError, ClientTimeout


class ApiError(Exception):
    """Receiver could not complete a request."""


class AuthError(ApiError):
    """Pairing is required."""


def validate_host(value):
    """Accept a hostname or IPv4 address, never a URL with credentials."""
    host = value.strip().lower()
    if not host or len(host) > 253 or not re.fullmatch(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?", host):
        raise ValueError("Enter a hostname or IPv4 address without a URL or port")
    return host


class CinemaApi:
    def __init__(self, session, host, port=8787, token=None):
        self.session = session
        self.base = f"http://{validate_host(host)}:{int(port)}"
        self.token = token

    async def request(self, method, path, body=None, *, authenticated=True, image=False):
        headers = {}
        if authenticated and self.token:
            headers["Cookie"] = "cf_session=" + self.token
        try:
            async with self.session.request(
                method, self.base + path, json=body, headers=headers,
                timeout=ClientTimeout(total=15), allow_redirects=False,
            ) as response:
                if response.status in (401, 403):
                    raise AuthError("Pair the receiver again")
                if not 200 <= response.status < 300:
                    raise ApiError(f"Receiver returned HTTP {response.status}")
                if image:
                    return await response.read(), response.content_type
                data = await response.json(content_type=None)
                if not isinstance(data, dict):
                    raise ApiError("Invalid receiver response")
                if path == "/api/pair":
                    cookie = SimpleCookie()
                    cookie.load(response.headers.get("Set-Cookie", ""))
                    if "cf_session" not in cookie:
                        raise AuthError("Receiver did not issue a session")
                    self.token = cookie["cf_session"].value
                return data
        except (ClientError, asyncio.TimeoutError, ValueError) as error:
            raise ApiError("Cannot communicate with Cinema Frame") from error

    async def state(self):
        data = await self.request("GET", "/api/state")
        if "phase" not in data or "version" not in data:
            raise ApiError("This is not a supported Cinema Frame receiver")
        return data

    async def start_pairing(self):
        await self.request("POST", "/api/pairing/start", {}, authenticated=False)

    async def pair(self, code):
        if not re.fullmatch(r"[0-9]{6}", code):
            raise AuthError("Enter the six-digit code")
        await self.request("POST", "/api/pair", {"code": code}, authenticated=False)
        await self.state()
        return self.token
