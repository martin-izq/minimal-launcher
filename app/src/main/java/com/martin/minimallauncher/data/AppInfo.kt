package com.martin.minimallauncher.data

/** A launchable app. Icon-free: the launcher is text-only (minimalist). */
data class AppInfo(
    val packageName: String,
    val originalLabel: String,
) {
    /** Visible label, honoring user renames. */
    fun displayLabel(renames: Map<String, String>): String =
        renames[packageName]?.takeIf { it.isNotBlank() } ?: originalLabel
}
