# Credentials and local access

This public repository contains source, fonts, a third-party native playback engine and its upstream source archives. It does not contain the developer's device preferences, Spotify account credentials, controller cookies, pairing code, database, signing keystore, personal logs or screenshots.

The six-digit pairing code, internal frame key and controller sessions are generated on the device and persisted in the app-private storage. Spotify device authorization credentials are also stored there. Android backup is disabled for both apps. These values are not embedded in source or APK assets.

`build.ps1` generates a local test signing key in ignored `build/`. Its test password is a build convention, not a Spotify/account password. Keep the actual private keystore private; production signing remains a separate task.

The receiver listens on TCP 8787 on the LAN and uses a pairing-derived `cf_session` cookie. It uses HTTP, not TLS. Six-digit pairing has a basic attempt limit; pairing-code display can be requested by a LAN client. This is a personal prototype, not a hardened public service. Do not expose port 8787 directly to the internet. Use an authenticated Home Assistant installation or a VPN for remote access.

The playback engine's HTTP endpoint binds to loopback. Home Assistant only needs the receiver controller session, never Spotify credentials or the internal frame key. Keep the generated Home Assistant values in `secrets.yaml`; do not commit or publish them. Changing/deleting app data removes sessions but also removes setup/history, so do not use that as routine session management.

Before publishing changes, run Gitleaks against both the working directory and Git history, inspect archives and check ignored files. Secret scanning reduces risk but is not a proof that every possible secret has been identified.
