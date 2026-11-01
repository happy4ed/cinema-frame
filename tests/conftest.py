"""Fixtures contain fictional data, never a real device/session."""
from copy import deepcopy
from unittest.mock import AsyncMock, patch

import pytest

STATE = {
    "phase": "playing", "version": 7, "confirmed": True, "display_mode": "height",
    "spotify_ready": True, "display_visible": True, "request": "orchestral",
    "volume": {"value": 40, "max": 100, "scope": "app"},
    "current": {"movie": {"id": "tt0000000", "title": "Example Movie", "rating": 8.0, "genres": ["Drama"]},
                "track": {"title": "Example Theme", "artist": "Example Composer", "duration": 180000, "uri": "spotify:track:example"}},
    "telemetry": {"player": {"track": {"position": 15000}}},
}


@pytest.fixture(autouse=True)
def custom_integrations(enable_custom_integrations):
    yield


@pytest.fixture
def mock_api():
    with patch("custom_components.cinema_frame.api.CinemaApi.state", new_callable=AsyncMock) as state, \
         patch("custom_components.cinema_frame.api.CinemaApi.request", new_callable=AsyncMock) as request:
        state.return_value = deepcopy(STATE)
        request.return_value = {"ok": True}
        yield state, request
