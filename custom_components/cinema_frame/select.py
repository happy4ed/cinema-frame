"""Poster fitting control."""
from homeassistant.components.select import SelectEntity
from homeassistant.exceptions import HomeAssistantError

from .const import DISPLAY_MODES
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([PosterMode(entry.runtime_data, entry)])


class PosterMode(CinemaEntity, SelectEntity):
    _attr_translation_key = "poster_mode"
    _attr_icon = "mdi:image-size-select-large"
    _attr_options = DISPLAY_MODES

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "poster_mode")

    @property
    def available(self):
        return super().available and "display_mode" in self.data

    @property
    def current_option(self):
        return self.data.get("display_mode")

    async def async_select_option(self, option):
        if option not in DISPLAY_MODES:
            raise HomeAssistantError("Unknown poster mode")
        await self.coordinator.command("/api/display", {"mode": option})
