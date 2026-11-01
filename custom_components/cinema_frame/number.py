"""Frame brightness and system music volume (not Cinema app volume)."""
from homeassistant.components.number import NumberEntity, NumberMode
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([HardwareNumber(entry.runtime_data, entry, "brightness"), HardwareNumber(entry.runtime_data, entry, "volume_music")])


class HardwareNumber(CinemaEntity, NumberEntity):
    _attr_native_min_value = 0
    _attr_native_step = 1
    _attr_mode = NumberMode.SLIDER

    def __init__(self, coordinator, entry, key):
        super().__init__(coordinator, entry, key)
        self.key = key
        self._attr_translation_key = key
        self._attr_icon = "mdi:brightness-6" if key == "brightness" else "mdi:volume-high"

    @property
    def available(self):
        return super().available and (self.key != "brightness" or self.data.get("write_settings", False))

    @property
    def native_max_value(self):
        return 255 if self.key == "brightness" else self.data.get("volume_music_max", 15)

    @property
    def native_value(self):
        return self.data[self.key]

    async def async_set_native_value(self, value):
        if self.key == "brightness":
            await self.coordinator.command("/api/brightness", {"value": int(value), "auto": False})
        else:
            await self.coordinator.command("/api/volume", {"stream": "music", "level": int(value)})
