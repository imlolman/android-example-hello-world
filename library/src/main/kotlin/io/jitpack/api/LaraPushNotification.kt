package io.jitpack.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.bumptech.glide.Glide
import org.json.JSONObject
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LaraPushNotification : FirebaseMessagingService() {
    private val config by lazy { LaraPush.getConfig() }
    private val gson = Gson()
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (config.debug) {
            Log.d("LaraPush", "Received message from: ${remoteMessage.from}")
            Log.d("LaraPush", "Message data: ${remoteMessage.data}")
        }

        createNotificationChannel()

        try {
            val notificationData = remoteMessage.data["notification"]?.let {
                JSONObject(it)
            }
            
            if (notificationData != null) {
                val title = notificationData.optString("title")
                val body = notificationData.optString("body")
                val icon = notificationData.optString("icon")
                val image = notificationData.optString("image")
                val clickAction = notificationData.optString("click_action")
                
                showNotification(title, body, icon, image, clickAction, remoteMessage)
            }
        } catch (e: Exception) {
            if (config.debug) {
                Log.e("LaraPush", "Error parsing notification", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        val client = OkHttpClient()
        
        val tags = getDeveloperTags()
        
        val json = JSONObject().apply {
            put("domain", config.applicationId)
            put("token", token)
            put("url", config.panelUrl)
            put("tags", JSONArray(tags))
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            json.toString()
        )

        val request = Request.Builder()
            .url("${config.panelUrl}api/token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (config.debug) {
                    Log.e("LaraPush", "Failed to send token", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (config.debug) {
                    Log.d("LaraPush", "Token sent successfully")
                }
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Push Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            
            if (config.debug) {
                Log.d("LaraPush", "Notification channel created")
            }
        }
    }

    private fun showNotification(
        title: String?,
        message: String?,
        iconUrl: String? = null,
        imageUrl: String? = null,
        clickAction: String? = null,
        remoteMessage: RemoteMessage
    ) {
        if (config.debug) {
            Log.d("LaraPush", "Showing notification - Title: $title, Message: $message")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            val notificationData = JSONObject(remoteMessage.data["notification"] ?: "{}")
            val apiUrl = notificationData.optString("api_url")

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            // Set up click action with API tracking
            clickAction?.let { action ->
                val intent = Intent(this, NotificationClickHandlerActivity::class.java).apply {
                    putExtra("click_action", action)
                    putExtra("api_url", apiUrl)
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.setContentIntent(pendingIntent)
            }

            // Handle action buttons
            val actions = notificationData.optJSONArray("actions")
            if (actions != null) {
                for (i in 0 until actions.length()) {
                    val action = actions.getJSONObject(i)
                    val actionTitle = action.getString("title")
                    val actionClickUrl = action.getString("click_action")
                    val actionApiUrl = action.optString("api_url", apiUrl)

                    val intent = Intent(this, NotificationClickHandlerActivity::class.java).apply {
                        putExtra("click_action", actionClickUrl)
                        putExtra("api_url", actionApiUrl)
                    }
                    
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        i + 100,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    builder.addAction(
                        NotificationCompat.Action.Builder(
                            android.R.drawable.ic_dialog_info,
                            actionTitle,
                            pendingIntent
                        ).build()
                    )
                }
            }

            // Load and set the large icon if available
            iconUrl?.let {
                try {
                    val bitmap = Glide.with(applicationContext)
                        .asBitmap()
                        .load(it)
                        .submit()
                        .get()
                    builder.setLargeIcon(bitmap)
                } catch (e: Exception) {
                    if (config.debug) {
                        Log.e("LaraPush", "Error loading notification icon", e)
                    }
                }
            }

            // Load and set the big picture if available
            imageUrl?.let {
                try {
                    val bitmap = Glide.with(applicationContext)
                        .asBitmap()
                        .load(it)
                        .submit()
                        .get()
                    builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
                } catch (e: Exception) {
                    if (config.debug) {
                        Log.e("LaraPush", "Error loading notification image", e)
                    }
                }
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())

        } catch (e: Exception) {
            if (config.debug) {
                Log.e("LaraPush", "Error creating notification", e)
            }
        }
    }

    private fun getDeveloperTags(): List<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(DEVELOPER_TAGS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    companion object {
        private const val CHANNEL_ID = "push_notification_channel"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "LaraPushPrefs"
        private const val DEVELOPER_TAGS_KEY = "developer_tags"
    }
}