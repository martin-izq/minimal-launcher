package com.martin.minimallauncher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

// System accessibility "color correction" secure settings. Monochromacy = full grayscale.
private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
private const val DALTONIZER = "accessibility_display_daltonizer"
private const val MODE_MONOCHROMACY = 0

/**
 * Whether we can toggle system grayscale. Needs [Manifest.permission.WRITE_SECURE_SETTINGS], which
 * a normal app can't request via a dialog — the user grants it once via adb:
 *   adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS
 */
fun canControlGrayscale(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

/** Turns the whole-system grayscale (color-correction monochromacy) on or off. Returns success. */
fun setSystemGrayscale(context: Context, enabled: Boolean): Boolean = runCatching {
    val cr = context.contentResolver
    if (enabled) {
        Settings.Secure.putInt(cr, DALTONIZER, MODE_MONOCHROMACY)
        Settings.Secure.putInt(cr, DALTONIZER_ENABLED, 1)
    } else {
        Settings.Secure.putInt(cr, DALTONIZER_ENABLED, 0)
    }
    true
}.getOrDefault(false)
