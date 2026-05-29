package com.martin.minimallauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** clickable sin ripple visible, para textos de navegación minimalistas. */
fun Modifier.clickableText(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        indication = null,
        interactionSource = androidx.compose.runtime.remember {
            androidx.compose.foundation.interaction.MutableInteractionSource()
        },
        onClick = onClick,
    )
}
