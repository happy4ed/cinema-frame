"""Submit a film, genre, era, or mood request."""
from homeassistant.components.text import TextEntity
from homeassistant.exceptions import HomeAssistantError

from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([MovieRequest(entry.runtime_data, entry)])


class MovieRequest(CinemaEntity, TextEntity):
    _attr_translation_key = "request"
    _attr_icon = "mdi:movie-search"
    _attr_native_min = 1
    _attr_native_max = 500

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "request")

    @property
    def native_value(self):
        return self.data.get("request", "")

    async def async_set_value(self, value):
        if not value.strip() or len(value) > 500:
            raise HomeAssistantError("Enter a request of 1–500 characters")
        await self.coordinator.command("/api/request", {"text": value.strip()})
