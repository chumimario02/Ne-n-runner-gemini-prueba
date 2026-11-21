package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun toggleScreenOrientation(context: Context, isCurrentlyLandscape: Boolean) {
    val activity = context.findActivity() ?: return
    activity.requestedOrientation = if (isCurrentlyLandscape) {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
