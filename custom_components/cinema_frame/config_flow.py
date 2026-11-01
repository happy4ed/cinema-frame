"""Pair from the HA UI using the code displayed on the frame."""
try:
    import probatio as vol
except ImportError:  # HA versions before the schema library migration.
    import voluptuous as vol

from homeassistant import config_entries
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .api import ApiError, AuthError, CinemaApi, validate_host
from .const import CONF_TOKEN, DEFAULT_PORT, DOMAIN


class CinemaConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    VERSION = 1
    _api = None
    _connection = None
    _existing = None

    async def async_step_user(self, user_input=None):
        errors = {}
        if user_input is not None:
            try:
                host = validate_host(user_input["host"])
                port = user_input["port"]
                for entry in self._async_current_entries():
                    if entry != self._existing and entry.data["host"] == host and entry.data["port"] == port:
                        return self.async_abort(reason="already_configured")
                self._connection = {"host": host, "port": port}
                self._api = CinemaApi(async_get_clientsession(self.hass), host, port)
                await self._api.start_pairing()
                return await self.async_step_pair()
            except ValueError:
                errors["host"] = "invalid_host"
            except ApiError:
                errors["base"] = "cannot_connect"
        defaults = self._existing.data if self._existing else {}
        return self.async_show_form(step_id="user", data_schema=vol.Schema({
            vol.Required("host", default=defaults.get("host", "")): str,
            vol.Required("port", default=defaults.get("port", DEFAULT_PORT)): vol.All(vol.Coerce(int), vol.Range(min=1, max=65535)),
        }), errors=errors)

    async def async_step_pair(self, user_input=None):
        errors = {}
        if user_input is not None:
            try:
                token = await self._api.pair(user_input["code"].strip())
                data = {**self._connection, CONF_TOKEN: token}
                data["kind"] = self._api.kind
                if self._existing:
                    return self.async_update_reload_and_abort(self._existing, data_updates=data)
                return self.async_create_entry(title="Frame Hardware" if data.get("kind") == "hardware" else "Cinema Frame", data=data)
            except AuthError:
                errors["base"] = "invalid_auth"
            except ApiError:
                errors["base"] = "cannot_connect"
        return self.async_show_form(step_id="pair", data_schema=vol.Schema({vol.Required("code"): str}), errors=errors)

    async def async_step_reauth(self, entry_data):
        self._existing = self._get_reauth_entry()
        return await self.async_step_user()

    async def async_step_reconfigure(self, user_input=None):
        self._existing = self._get_reconfigure_entry()
        return await self.async_step_user(user_input)
