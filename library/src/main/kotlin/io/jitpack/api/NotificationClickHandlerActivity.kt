package io.jitpack.api

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import okhttp3.*
import java.io.IOException

class NotificationClickHandlerActivity : Activity() {
    private val config by lazy { LaraPush.getConfig() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiUrl = intent.getStringExtra("api_url")
        val clickAction = intent.getStringExtra("click_action")

        apiUrl?.let {
            trackClick(it) {
                handleRedirect(clickAction)
            }
        } ?: handleRedirect(clickAction)
    }

    private fun trackClick(apiUrl: String, onComplete: () -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(apiUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (config.debug) {
                    Log.e("LaraPush", "Failed to track click", e)
                }
                onComplete()
            }

            override fun onResponse(call: Call, response: Response) {
                if (config.debug) {
                    Log.d("LaraPush", "Click tracked successfully")
                }
                onComplete()
            }
        })
    }

    private fun handleRedirect(clickAction: String?) {
        if (clickAction == null) {
            finish()
            return
        }

        val intent = when {
            clickAction.startsWith("activity://") -> {
                val activityName = clickAction.removePrefix("activity://")
                try {
                    val className = "${config.applicationId}.$activityName"
                    Intent().setClassName(packageName, className)
                } catch (e: Exception) {
                    if (config.debug) {
                        Log.e("LaraPush", "Error creating activity intent", e)
                    }
                    packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
                }
            }
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(clickAction))
        }

        startActivity(intent)
        finish()
    }
}