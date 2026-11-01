"""Hardware entries remain independent of Cinema playback and survive reload."""
from unittest.mock import AsyncMock, patch
import pytest
from homeassistant.const import STATE_UNAVAILABLE
from homeassistant.helpers import entity_registry as er
from pytest_homeassistant_custom_component.common import MockConfigEntry
from custom_components.cinema_frame.api import ApiError

STATE = {"service": "frame_diagnostics", "version": 1, "screen_on": True,
         "brightness": 150, "auto_brightness": False, "foreground_package": "com.happy.cinemaframe",
         "foreground_activity": "com.happy.cinemaframe.MainActivity", "volume_music": 5,
         "volume_music_max": 15, "accessibility_ready": True, "write_settings": True}


async def test_hardware_controls_and_reload(hass, mock_api):
    state, request = mock_api
    state.return_value = STATE.copy()
    entry = MockConfigEntry(domain="cinema_frame", title="Frame Hardware", data={
        "host": "frame.example", "port": 8788, "kind": "hardware", "token": "test-session"})
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    registry = er.async_get(hass)
    entities = {e.unique_id.removeprefix(entry.entry_id + "_"): e.entity_id
                for e in er.async_entries_for_config_entry(registry, entry.entry_id)}
    assert set(entities) == {"screen", "auto_brightness", "brightness", "volume_music", "foreground_app", "foreground_package"}
    assert hass.states.get(entities["screen"]).state == "on"
    assert hass.states.get(entities["foreground_app"]).state == "movie"
    for domain, service, key, data, path, body in [
        ("switch", "turn_off", "screen", {}, "/api/screen", {"on": False}),
        ("switch", "turn_on", "screen", {}, "/api/screen", {"on": True}),
        ("number", "set_value", "brightness", {"value": 123}, "/api/brightness", {"value": 123, "auto": False}),
        ("switch", "turn_on", "auto_brightness", {}, "/api/brightness", {"value": 150, "auto": True}),
        ("number", "set_value", "volume_music", {"value": 4}, "/api/volume", {"stream": "music", "level": 4}),
        ("select", "select_option", "foreground_app", {"option": "photo"}, "/api/app/foreground", {"package": "de.ozerov.fully"}),
        ("select", "select_option", "foreground_app", {"option": "movie"}, "/api/app/foreground", {"package": "com.happy.cinemaframe"}),
    ]:
        await hass.services.async_call(domain, service, {"entity_id": entities[key], **data}, blocking=True)
        request.assert_called_with("POST", path, body)
    state.side_effect = ApiError("offline")
    await entry.runtime_data.async_refresh()
    assert hass.states.get(entities["screen"]).state == STATE_UNAVAILABLE
    state.side_effect = None
    await entry.runtime_data.async_refresh()
    assert hass.states.get(entities["screen"]).state == "on"
    state.return_value["write_settings"] = False
    state.return_value["accessibility_ready"] = False
    await entry.runtime_data.async_refresh()
    assert hass.states.get(entities["brightness"]).state == STATE_UNAVAILABLE
    assert hass.states.get(entities["foreground_app"]).state == STATE_UNAVAILABLE
    state.return_value.update(STATE)
    assert await hass.config_entries.async_reload(entry.entry_id)
    await hass.async_block_till_done()
    assert hass.states.get(entities["screen"]).state == "on"
    assert entry.data["token"] == "test-session"
    await hass.config_entries.async_unload(entry.entry_id)


async def test_hardware_pairing_detection(hass):
    from custom_components.cinema_frame.api import CinemaApi
    async def paired(api, code):
        api.kind = "hardware"
        return "test-session"
    with patch.object(CinemaApi, "start_pairing", new_callable=AsyncMock), \
         patch.object(CinemaApi, "pair", paired), \
         patch("custom_components.cinema_frame.async_setup_entry", return_value=True):
        flow = await hass.config_entries.flow.async_init("cinema_frame", context={"source": "user"})
        flow = await hass.config_entries.flow.async_configure(flow["flow_id"], {"host": "frame.example", "port": 8788})
        result = await hass.config_entries.flow.async_configure(flow["flow_id"], {"code": "123456"})
        assert result["data"]["kind"] == "hardware"
        assert result["title"] == "Frame Hardware"
