package com.martin.foco.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.net.toUri

/** Reads launchable installed apps and resolves launch / info / uninstall intents. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return queryLauncherActivities(intent)
            .asSequence()
            .map { info ->
                AppInfo(
                    packageName = info.activityInfo.packageName,
                    originalLabel = info.loadLabel(pm).toString(),
                )
            }
            .filter { it.packageName != context.packageName } // don't list ourselves
            .distinctBy { it.packageName }
            .sortedBy { it.originalLabel.lowercase() }
            .toList()
    }

    private fun queryLauncherActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    fun launch(packageName: String) {
        val launch = pm.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the system's default clock/alarms app. */
    fun openAlarms() {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (showAlarms.resolveActivity(pm) != null && startActivitySafely(showAlarms)) return
        // Fallback: launch a known clock app.
        KNOWN_CLOCK_PACKAGES
            .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?.let { startActivitySafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafely(intent)
    }

    fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData("package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafely(intent)
    }

    private fun startActivitySafely(intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess

    private companion object {
        val KNOWN_CLOCK_PACKAGES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",   // Samsung
            "com.coloros.alarmclock",             // Oppo/Realme
            "com.miui.clock",                     // Xiaomi
        )
    }
}
