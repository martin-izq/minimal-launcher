package com.martin.minimallauncher.data

/** Una app lanzable. Sin íconos: el launcher es solo texto (minimalista). */
data class AppInfo(
    val packageName: String,
    val originalLabel: String,
) {
    /** Etiqueta visible considerando renombres del usuario. */
    fun displayLabel(renames: Map<String, String>): String =
        renames[packageName]?.takeIf { it.isNotBlank() } ?: originalLabel
}
