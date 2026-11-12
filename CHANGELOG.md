# Changelog

## 0.3.1

- Fixes the build: the chat helper read the room's event stream one level too high (`room.events` instead of `room.events.events`), so earlier versions did not compile and could not be installed from JitPack. Install 0.3.1 or later.

## 0.3.0 (did not build)

- `FaceCheck.launch` / `FaceCheck.attach`: hosted face verification in a WebView (adds the CAMERA permission).

## 0.1.0

- Initial release: video/voice calls, live streaming, and metered chat,
  built on LiveKit's official `livekit-android` client.
