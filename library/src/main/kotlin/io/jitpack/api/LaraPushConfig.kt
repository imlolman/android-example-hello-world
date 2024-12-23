package io.jitpack.api

data class LaraPushConfig(
    val panelUrl: String,
    val applicationId: String,
    val debug: Boolean = false
)