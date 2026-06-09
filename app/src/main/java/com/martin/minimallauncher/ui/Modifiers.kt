package com.martin.minimallauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * Clickable without a visible ripple, for the launcher's minimalist text navigation.
 *
 * Passing a null [interactionSource] and indication uses the modern clickable node
 * directly, avoiding the deprecated `composed { }` factory.
 */
fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)
