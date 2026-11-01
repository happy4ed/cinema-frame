"""Screen power and automatic brightness, independent of playback."""
from homeassistant.components.switch import SwitchEntity
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([ScreenPower(entry.runtime_data, entry), AutoBrightness(entry.runtime_data, entry)])


class ScreenPower(CinemaEntity, SwitchEntity):
    _attr_translation_key = "screen"
    _attr_icon = "mdi:monitor"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "screen")

    @property
    def available(self):
        return super().available and self.data.get("accessibility_ready", False)

    @property
    def is_on(self):
        return self.data["screen_on"]

    async def async_turn_on(self, **kwargs):
        await self.coordinator.command("/api/screen", {"on": True})

    async def async_turn_off(self, **kwargs):
        await self.coordinator.command("/api/screen", {"on": False})


class AutoBrightness(CinemaEntity, SwitchEntity):
    _attr_translation_key = "auto_brightness"
    _attr_icon = "mdi:brightness-auto"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "auto_brightness")

    @property
    def available(self):
        return super().available and self.data.get("write_settings", False)

    @property
    def is_on(self):
        return self.data["auto_brightness"]

    async def async_turn_on(self, **kwargs):
        await self.coordinator.command("/api/brightness", {"value": self.data["brightness"], "auto": True})

    async def async_turn_off(self, **kwargs):
        await self.coordinator.command("/api/brightness", {"value": self.data["brightness"], "auto": False})
