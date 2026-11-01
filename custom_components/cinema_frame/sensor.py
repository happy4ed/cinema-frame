"""Observed foreground activity; no tokens or app content."""
from homeassistant.components.sensor import SensorEntity
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([ForegroundSensor(entry.runtime_data, entry)])


class ForegroundSensor(CinemaEntity, SensorEntity):
    _attr_translation_key = "foreground_package"
    _attr_icon = "mdi:application"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "foreground_package")

    @property
    def available(self):
        return super().available and self.data.get("accessibility_ready", False)

    @property
    def native_value(self):
        return self.data.get("foreground_package") or None

    @property
    def extra_state_attributes(self):
        return {"activity": self.data.get("foreground_activity") or None}
