/**
 * Hosted face check for Android. Your SERVER creates the check (liveness.create in the Node/Python/Go/PHP SDKs, or
 * POST /v1/liveness/hosted) and hands the returned `url` to the app. No API key lives in the app: the link is the credential.
 *
 * Add `<uses-permission android:name="android.permission.CAMERA" />` to your manifest (this library declares it too).
 * The result the app receives is only id/status/passed/reference: confirm the verdict from your server before acting on it.
 *
 *   FaceCheck.launch(activity, url) { result -> if (result.passed) { /* ask your server */ } }   // full-screen
 *   FaceCheck.attach(activity, myWebView, url, listener)                                           // your own WebView
 */
package com.identifyorg.sdk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONObject

data class FaceCheckResult(val id: String, val status: String, val passed: Boolean, val reference: String?)

object FaceCheck {
    private const val REQUEST_CAMERA = 7421

    /** Configure [webView] to run the hosted check at [url] and report to [onResult]. Call from the main thread. */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity, webView: WebView, url: String, onResult: (FaceCheckResult) -> Unit, onReady: (() -> Unit)? = null) {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                activity.runOnUiThread {
                    val ok = activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (ok) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
                }
            }
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onResult(json: String) {
                try {
                    val d = JSONObject(json)
                    if (d.optString("source") != "identifyorg-liveness") return
                    activity.runOnUiThread {
                        when (d.optString("type")) {
                            "liveness.ready" -> onReady?.invoke()
                            "liveness.done" -> onResult(
                                FaceCheckResult(d.optString("id"), d.optString("status"), d.optBoolean("passed"), if (d.isNull("reference")) null else d.optString("reference")),
                            )
                        }
                    }
                } catch (_: Exception) { /* not ours */ }
            }
        }, "IdentifyOrgAndroid")
        webView.loadUrl(url)
    }

    /** A full-screen dialog holding the check. It closes itself when the check is decided. */
    fun launch(activity: Activity, url: String, onResult: (FaceCheckResult) -> Unit): Dialog {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val web = WebView(activity)
        web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setContentView(web)
        attach(activity, web, url, { r ->
            dialog.dismiss()
            onResult(r)
        })
        dialog.setOnDismissListener { web.destroy() }
        dialog.show()
        return dialog
    }
}
