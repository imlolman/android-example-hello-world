package io.jitpack.api

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class LaraPush private constructor(context: Context) {
    companion object {
        private var instance: LaraPush? = null
        private const val PREFS_NAME = "LaraPushPrefs"
        private const val DEVELOPER_TAGS_KEY = "developer_tags"
        private var config: LaraPushConfig? = null
        
        @JvmStatic
        fun init(context: Context, configuration: LaraPushConfig): LaraPush {
            config = configuration
            return instance ?: LaraPush(context.applicationContext).also { instance = it }
        }

        @JvmStatic
        fun getInstance(): LaraPush {
            return instance ?: throw IllegalStateException("LaraPush must be initialized first")
        }

        internal fun getConfig(): LaraPushConfig {
            return config ?: throw IllegalStateException("LaraPush must be initialized first")
        }
    }

    private val context: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Add tags to the user's subscription
     * @param tags Array of tags to add
     */
    suspend fun setTags(vararg tags: String) {
        val developerTags = getDeveloperTags().toMutableSet()
        developerTags.addAll(tags)
        
        if (developerTags != getDeveloperTags()) {
            saveDeveloperTags(developerTags.toList())
            updateSubscription()
        }
    }

    /**
     * Remove specific tags from the user's subscription
     * @param tags Array of tags to remove
     */
    suspend fun removeTags(vararg tags: String) {
        val developerTags = getDeveloperTags().toMutableSet()
        developerTags.removeAll(tags.toSet())
        
        if (developerTags != getDeveloperTags()) {
            saveDeveloperTags(developerTags.toList())
            updateSubscription()
        }
    }

    /**
     * Remove all tags from the user's subscription
     */
    suspend fun clearTags() {
        if (getDeveloperTags().isNotEmpty()) {
            saveDeveloperTags(emptyList())
            updateSubscription()
        }
    }

    /**
     * Get the current set of tags
     * @return Set of current tags
     */
    fun getTags(): Set<String> {
        return getDeveloperTags()
    }

    /**
     * Get the current Firebase messaging token
     * @return Current FCM token
     */
    suspend fun getToken(): String {
        return FirebaseMessaging.getInstance().token.await()
    }

    private fun getDeveloperTags(): Set<String> {
        val json = prefs.getString(DEVELOPER_TAGS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            
            gson.fromJson<List<String>>(json, type).toSet()
        } else {
            emptySet()
        }
    }

    private fun saveDeveloperTags(tags: List<String>) {
        val json = gson.toJson(tags)
        prefs.edit().putString(DEVELOPER_TAGS_KEY, json).apply()
    }

    private suspend fun updateSubscription() {
        try {
            val token = getToken()
            val intent = Intent("${config?.applicationId}.UPDATE_SUBSCRIPTION").apply {
                putExtra("token", token)
            }
            context.sendBroadcast(intent)
            
            if (config?.debug == true) {
                android.util.Log.d("LaraPush", "Subscription updated with token: $token")
                android.util.Log.d("LaraPush", "Current tags: ${getDeveloperTags()}")
            }
        } catch (e: Exception) {
            if (config?.debug == true) {
                android.util.Log.e("LaraPush", "Error updating subscription", e)
            }
        }
    }

    /**
     * Force refresh the Firebase token and update subscription
     */
    suspend fun refreshToken() {
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
            val newToken = getToken()
            updateSubscription()
            
            if (config?.debug == true) {
                android.util.Log.d("LaraPush", "Token refreshed: $newToken")
            }
        } catch (e: Exception) {
            if (config?.debug == true) {
                android.util.Log.e("LaraPush", "Error refreshing token", e)
            }
        }
    }

    /**
     * Check if push notifications are enabled
     * @return true if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    notificationManager.areNotificationsEnabled()
                }
                else -> {
                    true // For older versions, we can't really check
                }
            }
        } catch (e: Exception) {
            if (config?.debug == true) {
                android.util.Log.e("LaraPush", "Error checking notification status", e)
            }
            false
        }
    }
}