package com.martin.minimallauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings

/** Lee las apps instaladas lanzables y resuelve intents de lanzar / info / desinstalar. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION", "QueryPermissionsNeeded")
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved
            .asSequence()
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    originalLabel = ri.loadLabel(pm).toString(),
                )
            }
            .filter { it.packageName != context.packageName } // no mostrarnos a nosotros mismos
            .distinctBy { it.packageName }
            .sortedBy { it.originalLabel.lowercase() }
            .toList()
    }

    fun launch(packageName: String) {
        val launch = pm.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }

    /** Abre la app de reloj/alarmas predeterminada del sistema. */
    fun openAlarms() {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (showAlarms.resolveActivity(pm) != null) {
            try { context.startActivity(showAlarms); return } catch (_: Exception) {}
        }
        // Fallback: lanzar una app de reloj conocida
        val clockPackages = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",   // Samsung
            "com.coloros.alarmclock",             // Oppo/Realme
            "com.miui.clock",                     // Xiaomi
        )
        for (p in clockPackages) {
            val launch = pm.getLaunchIntentForPackage(p) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launch); return } catch (_: Exception) {}
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
