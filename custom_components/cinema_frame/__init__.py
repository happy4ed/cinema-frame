"""Cinema Frame integration; no add-on or MQTT broker required."""
from homeassistant.const import Platform
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .api import CinemaApi
from .const import CONF_TOKEN
from .coordinator import CinemaCoordinator

PLATFORMS = [Platform.MEDIA_PLAYER, Platform.SELECT, Platform.TEXT]


def platforms(entry):
    if entry.data.get("kind") == "hardware":
        return [Platform.SWITCH, Platform.NUMBER, Platform.SELECT, Platform.SENSOR]
    return PLATFORMS


async def async_setup_entry(hass, entry):
    api = CinemaApi(async_get_clientsession(hass), entry.data["host"],
                    entry.data["port"], entry.data[CONF_TOKEN])
    coordinator = CinemaCoordinator(hass, entry, api)
    await coordinator.async_config_entry_first_refresh()
    entry.runtime_data = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, platforms(entry))
    return True


async def async_unload_entry(hass, entry):
    return await hass.config_entries.async_unload_platforms(entry, platforms(entry))
