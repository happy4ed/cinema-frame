"""Explicitly send the editable movie/mood draft."""
from homeassistant.components.button import ButtonEntity
from homeassistant.exceptions import HomeAssistantError
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([SendRequest(entry.runtime_data, entry)])


class SendRequest(CinemaEntity, ButtonEntity):
    _attr_translation_key = "send_request"
    _attr_icon = "mdi:send"

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "send_request")

    async def async_press(self):
        text = self.coordinator.config_entry.options.get("request_draft", self.data.get("request", "")).strip()
        if not text or len(text) > 500:
            raise HomeAssistantError("Enter a movie or mood request first")
        await self.coordinator.command("/api/request", {"text": text})
