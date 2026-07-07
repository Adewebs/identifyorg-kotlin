/**
 * IdentifyOrg — Kotlin/Android SDK (video/voice calls, live streaming,
 * metered chat), built on LiveKit's official Android client
 * (`io.livekit:livekit-android`). IdentifyOrg adds auth/token-fetch and
 * metering; LiveKit does the actual WebRTC media routing — same
 * architecture as the JS, React Native, and Flutter SDKs.
 *
 * SECURITY: only use a PUBLISHABLE key (io_test_pub_.../io_live_pub_...)
 * here. Secret keys must stay on your backend (see the Node/Python/Go SDKs).
 *
 * API verified against livekit-android's published source
 * (LiveKit.create / Room.connect / LocalParticipant.publishData /
 * RoomEvent.DataReceived) since this file is written outside an Android
 * toolchain — run a Gradle build in your own project as a final check.
 */
package com.identifyorg.sdk

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

private const val DEFAULT_API_BASE = "https://api.identifyorg.com"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class IdentifyOrgApiException(val status: Int, val code: String?, message: String) : IOException(message)

enum class IdentifyOrgSessionType(val wireName: String) {
    VIDEO("video"), VOICE("voice"), STREAM("stream"),
}

data class RealtimeTokenInfo(
    val sessionId: String,
    val roomName: String,
    val token: String,
    val url: String,
    val type: String,
    val pricePerMinute: Double,
    val currency: String,
    val isTest: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject) = RealtimeTokenInfo(
            sessionId = json.getString("session_id"),
            roomName = json.getString("room_name"),
            token = json.getString("token"),
            url = json.getString("url"),
            type = json.getString("type"),
            pricePerMinute = json.getDouble("price_per_minute"),
            currency = json.getString("currency"),
            isTest = json.getBoolean("is_test"),
        )
    }
}

/** Entry point for the SDK. Construct once with a publishable API key. */
class IdentifyOrg(
    private val apiKey: String,
    private val apiBase: String = DEFAULT_API_BASE,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    init {
        if (!Regex("^io_(test|live)_pub_").containsMatchIn(apiKey)) {
            android.util.Log.w(
                "IdentifyOrg",
                "Use a publishable key (io_test_pub_.../io_live_pub_...) on-device. " +
                    "Mint secret-key calls from your backend instead.",
            )
        }
    }

    private suspend fun post(path: String, body: Map<String, String?>): JSONObject =
        withContext(Dispatchers.IO) {
            val json = JSONObject()
            body.forEach { (k, v) -> if (v != null) json.put(k, v) }
            val request = Request.Builder()
                .url(apiBase + path)
                .addHeader("X-IdentifyOrg-Key", apiKey)
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                val data = JSONObject(bodyStr)
                if (!response.isSuccessful) {
                    val err = data.optJSONObject("error")
                    throw IdentifyOrgApiException(
                        response.code,
                        err?.optString("code"),
                        err?.optString("message") ?: response.message,
                    )
                }
                data
            }
        }

    /**
     * Join (or create) a video/voice/live-stream room. Returns a connected
     * LiveKit [Room] plus the IdentifyOrg token metadata. Render tracks with
     * livekit-android's own Compose/View components against the room.
     */
    suspend fun joinCall(
        context: Context,
        type: IdentifyOrgSessionType,
        identity: String,
        displayName: String? = null,
        roomName: String? = null,
        viewer: Boolean = false,
        enableCamera: Boolean = true,
        enableMicrophone: Boolean = true,
    ): IdentifyOrgCall {
        val json = post(
            "/v1/streaming/${type.wireName}/token",
            mapOf(
                "identity" to identity,
                "display_name" to displayName,
                "room_name" to roomName,
                "role" to if (viewer) "viewer" else "publisher",
            ),
        )
        val info = RealtimeTokenInfo.fromJson(json)

        val room = LiveKit.create(context)
        room.connect(info.url, info.token)

        if (!viewer) {
            val wantVideo = enableCamera && type != IdentifyOrgSessionType.VOICE
            if (wantVideo) room.localParticipant.setCameraEnabled(true)
            if (enableMicrophone) room.localParticipant.setMicrophoneEnabled(true)
        }

        return IdentifyOrgCall(room, info)
    }

    /**
     * Join (or create) a metered chat channel — a data-only LiveKit room (no
     * audio/video grants), much cheaper to run/bill than a call.
     */
    suspend fun joinChat(
        context: Context,
        visitorId: String,
        visitorName: String? = null,
        channelId: String? = null,
    ): IdentifyOrgChat {
        val json = post(
            "/v1/chat/token",
            mapOf("visitor_id" to visitorId, "visitor_name" to visitorName, "channel_id" to channelId),
        )
        val info = RealtimeTokenInfo.fromJson(json)

        val room = LiveKit.create(context)
        room.connect(info.url, info.token)
        return IdentifyOrgChat(room, info)
    }
}

/** A joined call/stream. Wraps a connected LiveKit [Room]. */
class IdentifyOrgCall(val room: Room, val info: RealtimeTokenInfo) {
    fun leave() = room.disconnect()
}

data class IdentifyOrgChatMessage(val text: String, val from: String, val ts: Long, val meta: Any?)

/**
 * A joined chat channel. Uses LiveKit's reliable data channel under the
 * hood — call [onMessage] to subscribe (within your own CoroutineScope),
 * [send] to publish.
 */
class IdentifyOrgChat(val room: Room, val info: RealtimeTokenInfo) {

    /** Collects room.events for DataReceived messages on [scope] until it's cancelled.
     * Uses `collect` inside `scope.launch`, matching LiveKit's own documented
     * usage pattern for room.events (it isn't a plain kotlinx.coroutines Flow,
     * so `.onEach {}.launchIn()` doesn't type-check against it). */
    fun onMessage(scope: CoroutineScope, handler: (IdentifyOrgChatMessage) -> Unit) {
        scope.launch {
            room.events.collect { event ->
                if (event !is RoomEvent.DataReceived) return@collect
                runCatching {
                    val text = String(event.data, StandardCharsets.UTF_8)
                    val json = JSONObject(text)
                    handler(
                        IdentifyOrgChatMessage(
                            text = json.getString("text"),
                            from = event.participant?.identity?.value ?: "unknown",
                            ts = json.getLong("ts"),
                            meta = json.opt("meta"),
                        ),
                    )
                }
            }
        }
    }

    suspend fun send(text: String, meta: Any? = null) {
        val payload = JSONObject().apply {
            put("text", text)
            put("meta", meta)
            put("ts", System.currentTimeMillis())
        }.toString().toByteArray(StandardCharsets.UTF_8)
        room.localParticipant.publishData(payload, reliability = DataPublishReliability.RELIABLE)
    }

    fun leave() = room.disconnect()
}
