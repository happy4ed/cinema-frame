"""Pair a LAN receiver and write private HA secrets without printing credentials."""
import argparse
import getpass
import http.cookies
import json
import os
from pathlib import Path
import urllib.request
import urllib.parse


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="Frame IPv4 address or LAN hostname")
    parser.add_argument("--output", type=Path, default=Path("secrets.local.yaml"))
    args = parser.parse_args()
    if any(c in args.host for c in "/:@?#"):
        parser.error("Use only the LAN hostname or IPv4 address, without URL or port")
    if args.output.name not in ("secrets.yaml", "secrets.local.yaml"):
        parser.error("Output must be named secrets.yaml or secrets.local.yaml (ignored by Git)")
    if args.output.exists():
        parser.error("Output already exists; merge it manually or choose another directory")
    base = f"http://{args.host}:8787"
    def post(path, body):
        req = urllib.request.Request(base + path, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
        return urllib.request.urlopen(req, timeout=15)
    with post("/api/pairing/start", {}) as response:
        response.read()
    code = getpass.getpass("Code shown on the frame (hidden): ")
    if len(code) != 6 or not code.isdigit():
        parser.error("Expected the six-digit frame code")
    with post("/api/pair", {"code": code}) as response:
        cookie = http.cookies.SimpleCookie()
        cookie.load(response.headers.get("Set-Cookie", ""))
        response.read()
    if "cf_session" not in cookie:
        raise RuntimeError("Receiver did not issue a controller session")
    values = {f"cinema_frame_{name}_url": base + path for name, path in {
        "state": "/api/state", "control": "/api/control", "request": "/api/request", "volume": "/api/volume"
    }.items()}
    values["cinema_frame_cookie"] = "cf_session=" + cookie["cf_session"].value
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        for name, value in values.items():
            output.write(name + ": " + json.dumps(value) + "\n")
    print("Paired. Private values written to", args.output, "(do not publish this file).")


if __name__ == "__main__":
    main()
