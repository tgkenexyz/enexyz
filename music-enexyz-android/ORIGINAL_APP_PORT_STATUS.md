# Music ENEXYZ — status of requested full Android port

## Important correction

The debug APK previously built by this branch is a **separate local music player**, not a full port of `Music_ENEXYZ.exe`. It does not meet the requested result; do not treat it as the Android release of the Windows application.

## Actual Windows files inspected (2026-11-01)

- `Music_ENEXYZ.exe` is a Windows x64 Electron runtime (244,773,888 bytes), without an appended application archive.
- The previously uploaded `Music_ENEXYZ_Setup_x64_v3.exe` is an NSIS installer containing `resources/app.asar`.
- That ASAR was recovered successfully (251,294,283 bytes); it contains 35,295 archived files.
- Original renderer: `dist/index.html`, 15 JS/CSS assets in `dist/assets`, branded styling, and original application icon.
- Desktop logic: `dist-electron/main.js`, `dist-electron/preload.cjs`, `dist-electron/network-direct.js` and native module `snap_preview.node`.
- The original desktop preload exposes `window.linerElectron` and dozens of Electron IPC methods.

## What the real port requires

1. Adapt the original renderer to Android WebView or a native UI while preserving its behavior.
2. Implement Android replacements for Electron IPC: account/session, signing, network, music/audio playback, downloads and caching.
3. Adapt native YouTube/stream integrations as supported on Android, while respecting authorization and service restrictions.
4. Implement MediaSession/background playback and storage using Android platform APIs.
5. Test login, catalog browsing, search, playlists, playback, downloads and background behavior on a real Android device.

The UI/JS resources are available from the user's locally provided installer. Those extracted third-party assets have **not** been pushed into this public repository automatically.

## Status

**Full functional Android port: NOT COMPLETE.**

The current branch is an obsolete prototype created for a different task interpretation. A separately packaged Android WebView UI migration experiment exists, but it has no verified end-to-end account or streaming implementation, so it must not be marketed as fully functional.
