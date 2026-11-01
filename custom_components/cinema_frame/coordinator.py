"""One shared state poll for every entity on a receiver."""
from datetime import timedelta
import logging

from homeassistant.exceptions import ConfigEntryAuthFailed, HomeAssistantError
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed
from homeassistant.util import dt as dt_util

from .api import ApiError, AuthError
from .const import DOMAIN


class CinemaCoordinator(DataUpdateCoordinator):
    def __init__(self, hass, entry, api):
        super().__init__(hass, logging.getLogger(__name__), name=DOMAIN,
                         config_entry=entry, update_interval=timedelta(seconds=3))
        self.api = api
        self.updated_at = None

    async def _async_update_data(self):
        try:
            data = await self.api.state()
        except AuthError as error:
            raise ConfigEntryAuthFailed("Cinema Frame pairing expired") from error
        except ApiError as error:
            raise UpdateFailed(str(error)) from error
        self.updated_at = dt_util.utcnow()
        return data

    async def command(self, path, body):
        try:
            await self.api.request("POST", path, body)
        except AuthError as error:
            self.config_entry.async_start_reauth(self.hass)
            raise HomeAssistantError("Pair Cinema Frame again") from error
        except ApiError as error:
            raise HomeAssistantError(str(error)) from error
        await self.async_request_refresh()
