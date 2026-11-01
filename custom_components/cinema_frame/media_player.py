"""Native HA media controls and authenticated poster proxy."""
from homeassistant.components.media_player import MediaPlayerEntity, MediaPlayerEntityFeature, MediaPlayerState, MediaType
from homeassistant.exceptions import HomeAssistantError

from .api import ApiError
from .entity import CinemaEntity


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([CinemaPlayer(entry.runtime_data, entry)])


class CinemaPlayer(CinemaEntity, MediaPlayerEntity):
    _attr_name = None
    _attr_icon = "mdi:movie-open-play"
    _attr_supported_features = (MediaPlayerEntityFeature.PLAY | MediaPlayerEntityFeature.PAUSE |
        MediaPlayerEntityFeature.NEXT_TRACK | MediaPlayerEntityFeature.SEEK |
        MediaPlayerEntityFeature.VOLUME_SET | MediaPlayerEntityFeature.PLAY_MEDIA)
    _attr_media_content_type = MediaType.MUSIC

    def __init__(self, coordinator, entry):
        super().__init__(coordinator, entry, "player")

    @property
    def current(self):
        return self.data.get("current") or {}

    @property
    def track(self):
        return self.current.get("track") or {}

    @property
    def state(self):
        return {"playing": MediaPlayerState.PLAYING, "paused": MediaPlayerState.PAUSED,
                "loading": MediaPlayerState.BUFFERING, "searching": MediaPlayerState.BUFFERING}.get(self.data.get("phase"), MediaPlayerState.IDLE)

    @property
    def volume_level(self):
        return (self.data.get("volume") or {}).get("value", 0) / 100

    @property
    def media_title(self):
        return self.track.get("title")

    @property
    def media_artist(self):
        return self.track.get("artist")

    @property
    def media_album_name(self):
        return (self.current.get("movie") or {}).get("title")

    @property
    def media_content_id(self):
        return self.track.get("uri")

    @property
    def media_duration(self):
        return self.track.get("duration", 0) / 1000 or None

    @property
    def media_position(self):
        if not self.data.get("confirmed"):
            return None
        track = ((self.data.get("telemetry") or {}).get("player") or {}).get("track") or {}
        position = track.get("position")
        return position / 1000 if position is not None else None

    @property
    def media_position_updated_at(self):
        return self.coordinator.updated_at if self.media_position is not None else None

    @property
    def media_image_url(self):
        # HA uses this as an artwork change key; credentials are sent only below.
        movie = self.current.get("movie") or {}
        return f"{self.coordinator.api.base}/api/poster?movie={movie.get('id', '')}" if self.current else None

    async def async_get_media_image(self):
        if not self.current:
            return None, None
        try:
            return await self.coordinator.api.request("GET", "/api/poster", image=True)
        except ApiError:
            return None, None

    @property
    def extra_state_attributes(self):
        movie = self.current.get("movie") or {}
        return {"movie_title": movie.get("title"), "imdb_id": movie.get("id"),
                "imdb_rating": movie.get("rating"), "genres": movie.get("genres"),
                "display_mode": self.data.get("display_mode"), "phase": self.data.get("phase"),
                "spotify_ready": self.data.get("spotify_ready"), "display_visible": self.data.get("display_visible")}

    async def async_media_play(self):
        await self.coordinator.command("/api/control", {"action": "resume"})

    async def async_media_pause(self):
        await self.coordinator.command("/api/control", {"action": "pause"})

    async def async_media_next_track(self):
        await self.coordinator.command("/api/control", {"action": "next"})

    async def async_set_volume_level(self, volume):
        await self.coordinator.command("/api/volume", {"value": round(max(0, min(1, volume)) * 100)})

    async def async_media_seek(self, position):
        if not self.data.get("confirmed") or self.media_duration is None or not 0 <= position < self.media_duration:
            raise HomeAssistantError("Seek within the currently playing track")
        await self.coordinator.command("/api/control", {"action": "seek", "version": self.data["version"], "position": round(position * 1000)})

    async def async_play_media(self, media_type, media_id, **kwargs):
        if media_type != "cinema_frame" or not media_id.strip() or len(media_id) > 500:
            raise HomeAssistantError("Use media_content_type: cinema_frame and a request of 1–500 characters")
        await self.coordinator.command("/api/request", {"text": media_id.strip()})
