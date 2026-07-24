package com.martin.foco.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Clickable without a visible ripple, for the launcher's minimalist text navigation.
 *
 * Passing a null [interactionSource] and indication uses the modern clickable node
 * directly, avoiding the deprecated `composed { }` factory.
 */
fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

/**
 * Pads by the status bar height even while the bar is hidden ([WindowInsets.statusBarsIgnoringVisibility]),
 * so toggling the "hide status bar" setting never shifts the layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.stableStatusBarsPadding(): Modifier =
    windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
