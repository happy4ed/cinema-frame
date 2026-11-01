"""Exercise actual HA config entries, platforms and service dispatch."""
from unittest.mock import AsyncMock, patch

import pytest
from homeassistant.const import STATE_UNAVAILABLE
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import entity_registry as er
from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.cinema_frame.api import ApiError, AuthError


async def setup(hass):
    entry = MockConfigEntry(domain="cinema_frame", title="Cinema Frame", data={
        "host": "frame.example", "port": 8787, "token": "test-session-not-a-secret"})
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    registry = er.async_get(hass)
    entities = {e.domain: e.entity_id for e in er.async_entries_for_config_entry(registry, entry.entry_id)}
    return entry, entities


async def test_entities_and_controls(hass, mock_api):
    state, request = mock_api
    entry, entities = await setup(hass)
    player = entities["media_player"]
    assert hass.states.get(player).state == "playing"
    attrs = hass.states.get(player).attributes
    assert attrs["media_title"] == "Example Theme"
    assert attrs["media_duration"] == 180
    assert attrs["media_position"] == 15
    assert attrs["volume_level"] == .4
    assert "test-session-not-a-secret" not in str(attrs)
    for service, data, path, body in [
        ("media_pause", {}, "/api/control", {"action": "pause"}),
        ("media_play", {}, "/api/control", {"action": "resume"}),
        ("media_next_track", {}, "/api/control", {"action": "next"}),
        ("volume_set", {"volume_level": .27}, "/api/volume", {"value": 27}),
        ("media_seek", {"seek_position": 42}, "/api/control", {"action": "seek", "version": 7, "position": 42000}),
        ("play_media", {"media_content_type": "cinema_frame", "media_content_id": "라라랜드 같은 음악"}, "/api/request", {"text": "라라랜드 같은 음악"}),
    ]:
        await hass.services.async_call("media_player", service, {"entity_id": player, **data}, blocking=True)
        request.assert_called_with("POST", path, body)
    await hass.services.async_call("select", "select_option", {"entity_id": entities["select"], "option": "blur"}, blocking=True)
    request.assert_called_with("POST", "/api/display", {"mode": "blur"})
    request.reset_mock()
    await hass.services.async_call("text", "set_value", {"entity_id": entities["text"], "value": "1990년대 액션"}, blocking=True)
    request.assert_not_called()
    assert entry.options["request_draft"] == "1990년대 액션"
    await hass.services.async_call("button", "press", {"entity_id": entities["button"]}, blocking=True)
    request.assert_called_with("POST", "/api/request", {"text": "1990년대 액션"})
    with pytest.raises(HomeAssistantError):
        await hass.services.async_call("media_player", "media_seek", {"entity_id": player, "seek_position": 999}, blocking=True)
    with pytest.raises(HomeAssistantError):
        await hass.services.async_call("media_player", "play_media", {"entity_id": player, "media_content_type": "url", "media_content_id": "https://example.org/"}, blocking=True)
    state.side_effect = ApiError("Offline")
    await entry.runtime_data.async_refresh()
    assert hass.states.get(player).state == STATE_UNAVAILABLE
    state.side_effect = None
    await entry.runtime_data.async_refresh()
    assert hass.states.get(player).state == "playing"
    assert await hass.config_entries.async_unload(entry.entry_id)


async def test_poster_proxy_and_old_receiver(hass, mock_api):
    from custom_components.cinema_frame.media_player import CinemaPlayer
    state, request = mock_api
    state.return_value.pop("display_mode")
    entry, entities = await setup(hass)
    assert hass.states.get(entities["select"]).state == STATE_UNAVAILABLE
    player = CinemaPlayer(entry.runtime_data, entry)
    request.return_value = (b"image", "image/jpeg")
    assert await player.async_get_media_image() == (b"image", "image/jpeg")
    request.assert_called_with("GET", "/api/poster", image=True)
    request.side_effect = ApiError("Offline")
    assert await player.async_get_media_image() == (None, None)
    await hass.config_entries.async_unload(entry.entry_id)


async def test_pairing_flow(hass):
    with patch("custom_components.cinema_frame.config_flow.CinemaApi.start_pairing", new_callable=AsyncMock), \
         patch("custom_components.cinema_frame.config_flow.CinemaApi.pair", new_callable=AsyncMock) as pair, \
         patch("custom_components.cinema_frame.async_setup_entry", return_value=True):
        result = await hass.config_entries.flow.async_init("cinema_frame", context={"source": "user"})
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {"host": "http://wrong/", "port": 8787})
        assert result["errors"] == {"host": "invalid_host"}
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {"host": "frame.example", "port": 8787})
        assert result["step_id"] == "pair"
        pair.side_effect = AuthError("Wrong code")
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {"code": "000000"})
        assert result["errors"] == {"base": "invalid_auth"}
        pair.side_effect = None
        pair.return_value = "test-session-not-a-secret"
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {"code": "123456"})
        assert result["type"] == "create_entry"
        assert "code" not in result["data"]
        assert result["data"]["token"] == "test-session-not-a-secret"
        await hass.async_block_till_done()


async def test_auth_failure_starts_reauth(hass, mock_api):
    state, _ = mock_api
    entry, _ = await setup(hass)
    state.side_effect = AuthError("Expired")
    await entry.runtime_data.async_refresh()
    await hass.async_block_till_done()
    assert any(flow["context"]["source"] == "reauth" for flow in hass.config_entries.flow.async_progress())
    await hass.config_entries.async_unload(entry.entry_id)
