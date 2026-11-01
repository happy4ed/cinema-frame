# Frame hardware control (HACS 0.2)

Cinema playback and frame hardware are independent connections in the **same Cinema Frame HACS integration**. Keep the existing port 8787 entry; add a second entry for port **8788**. No Fully Plus, MQTT, add-on, root, or runtime ADB is required.

## Install and pair

1. Install Frame Diagnostics (`com.happy.frame.diagnostics`). Existing installations must be updated with the same signing key; do not uninstall or clear data.
2. Open Frame Gesture Diagnostics once. This Android 10 firmware may disable the accessibility service during an APK update; check and re-enable it after updating. Enable **Frame Edge Gestures** in Android accessibility settings. Its service receives window package/activity events and performs app switching and screen locking. Existing edge gestures remain available.
3. Use **밝기 제어 권한 설정** to allow Android **Modify system settings**. Without this permission, brightness entities are unavailable; other controls continue working.
4. Update Cinema Frame in HACS and restart Home Assistant.
5. Settings → Devices & services → Add integration → Cinema Frame. Enter the frame LAN address and port **8788**. A five-minute, six-digit code appears centered over the current app. Enter it in HA. An existing 8787 connection is unaffected.
6. Reserve the frame's LAN IP in the router, or use a stable local hostname. Keep 8787 and 8788 private; never port-forward them or publish them through a proxy/tunnel.

For playback pairing, use port **8787**. Starting the HA connection automatically requests the Cinema pairing screen; Cinema Frame must be visible to read it. The small **연결** button at bottom right also opens it, including through scrcpy. When Fully is visible, the hardware entry can switch to Cinema Frame first. Codes for 8787 and 8788 are separate and not interchangeable.

## Entities

Hardware entry (8788):
- Screen switch: waking does **not** launch an app.
- Brightness number: 0–255; setting it selects manual brightness.
- Automatic brightness switch: preserves the stored manual brightness value.
- Foreground app select: Photo (Fully) / Movie (Cinema Frame).
- Foreground package sensor, with activity attribute. It reports the last activity observed by accessibility; it may be unknown just after startup. Dialogs are not mistaken for activities.
- System music volume number: Android music-stream steps. This is separate from the Cinema app's volume in the 8787 media player.

The entry polls every three seconds. Offline entries become unavailable and recover automatically. Tokens are stored in HA's config entry and persist across reload/restart; credentials never appear in entity attributes or artwork URLs. Reconfigure the entry if its address changes. Reauthentication requests a new screen code.

## Local API

HTTP port 8788, IPv4 RFC1918 / loopback peers only. All control and state endpoints require `Authorization: Bearer <token>` or `Cookie: cf_session=<token>`. Browser Origin requests are rejected; there is no CORS interface. HTTP is intended only for a trusted LAN.

- `POST /api/pairing/start {}`: display code, 5-minute validity, one remote request/minute. This and `/api/pair` are the only unauthenticated endpoints.
- `POST /api/pair {"code":"123456"}`: at most five attempts per code. Returns token in `Set-Cookie`. The code is single-use; only token hashes are stored by the frame. Up to eight controller tokens remain valid.
- `GET /api/state`: service/version, screen_on, brightness, auto_brightness, foreground_package, foreground_activity, volume_music/max, battery percentage (null if unknown), plugged, accessibility_ready, write_settings.
- `POST /api/app/foreground {"package":"com.happy.cinemaframe","activity":".MainActivity"}`. Only this component and `de.ozerov.fully/.FullyActivity` are allowed; activity is optional. Does not implicitly wake the screen.
- `POST /api/screen {"on":true}`: WakeLock wake only. False invokes accessibility GLOBAL_ACTION_LOCK_SCREEN. Devices with a secure lock screen still require local unlock; this does not bypass a PIN.
- `POST /api/brightness {"value":144,"auto":false}`: integer 0–255; auto defaults to false. Missing WRITE_SETTINGS produces 409.
- `POST /api/volume {"stream":"music","level":5}`: integer 0–volume_music_max.

State/control without authentication returns 401. Invalid inputs return 400, forbidden apps 403, inaccessible accessibility actions 503. Commands acknowledge acceptance; poll state to observe asynchronous effects. Frame Diagnostics has a local **HA 연결 토큰 모두 해제** action to revoke all hardware controllers.

## Lifecycle and limitations

The foreground service starts on boot, package update, and accessibility connection, and uses START_STICKY. A partial CPU wake lock keeps the server reachable with the display off; the existing accessibility service holds a Wi-Fi lock. This is intended for an always-powered frame. Android force-stop disables automatic starts until the app is opened again. On this Android 10 device, adding INTERNET to the previously installed diagnostics app required one reboot before sockets worked; a fresh installation includes that permission from the start.

The original cursor-diagnostics feature can optionally use an ADB-granted DUMP permission. **None of the LAN endpoints require it.** Updating preserves existing gesture settings and other apps. Screen wake never launches Fully, Cinema, or the diagnostics activity. Separate Fully/HA automations can still independently change the foreground app; remove conflicting wake automations if necessary.

## Verification

Live Lenovo Android 10 testing: LAN pairing, unauthenticated 401, browser-origin rejection, app/activity allowlist, strict input types/ranges, brightness, system volume, four photo/movie switches in 0.8–2.0 seconds, screen off with LAN still reachable, and wake retaining Cinema Frame. Boot auto-start was observed. After a full reboot, the service recovered over Wi-Fi in 40.3 seconds using the existing token, with accessibility and WRITE_SETTINGS still enabled.

HA tests exercise actual platform setup, service dispatch, offline recovery, permission-dependent availability, config-entry reload and hardware pairing detection in the GitHub Actions HA runtime. This is not a claim that the user's separate HA server has already been installed or restarted.
