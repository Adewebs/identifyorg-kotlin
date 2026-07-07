# identifyorg-android

Kotlin/Android SDK for IdentifyOrg calls, live streaming, and chat —
built on LiveKit's official `io.livekit:livekit-android` client. API
verified against livekit-android's published source (`LiveKit.create`,
`Room.connect`, `LocalParticipant.publishData`, `RoomEvent.DataReceived`)
since this module is written outside an Android/Gradle toolchain — run a
Gradle build in your own project as a final check before shipping.

## Install (`settings.gradle.kts` + module `build.gradle.kts`)

```kotlin
dependencies {
    implementation("com.identifyorg:android-sdk:0.1.0")
}
```

Add camera/microphone permissions to your **app's** manifest (library only
declares `INTERNET`) and request them at runtime before joining a call —
see [LiveKit Android's quickstart](https://docs.livekit.io/home/quickstarts/android/)
for the standard permission-request boilerplate.

## Video call

```kotlin
val identifyorg = IdentifyOrg(apiKey = "io_test_pub_...")

lifecycleScope.launch {
    val call = identifyorg.joinCall(
        context = applicationContext,
        type = IdentifyOrgSessionType.VIDEO,
        identity = "user-123",
        displayName = "Ada",
    )
    // Render with livekit-android's Compose components against call.room,
    // e.g. VideoRenderer / ParticipantItem from the LiveKit Compose sample.
    onDestroy { call.leave() }
}
```

## Live streaming (one-to-many)

```kotlin
val stream = identifyorg.joinCall(context, IdentifyOrgSessionType.STREAM, identity = "host-1")

val view = identifyorg.joinCall(
    context, IdentifyOrgSessionType.STREAM,
    roomName = stream.info.roomName,
    identity = "viewer-99",
    viewer = true, // subscribe-only — cheaper per-minute rate
)
```

## Live chat

```kotlin
val chat = identifyorg.joinChat(context, visitorId = "visitor-42", visitorName = "Guest")
chat.onMessage(lifecycleScope) { msg -> println("${msg.from}: ${msg.text}") }
lifecycleScope.launch { chat.send("Hello!") }
```

## Security

Only ever use a **publishable** key (`io_test_pub_.../io_live_pub_...`) on
device. Secret keys must be minted server-side (Node/Python/Go SDKs) since
they can also verify BVN/NIN/FRSC and touch billing.

## Pricing

`GET https://api.identifyorg.com/v1/pricing` (no auth) returns live rates —
voice ₦1.00/min, video ₦2.50/min, stream ₦1.50/viewer-min, chat ₦0.20/min,
charged when the session ends based on actual usage.
