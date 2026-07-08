package com.martin.minimallauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A single text row for an app, with optional favorite star and long-press support. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    fontSizeSp: Int? = null,
    textAlign: TextAlign? = null,
    blocked: Boolean = false,
    badge: Int = 0,
) {
    // Indicators (badge/star) sit on the side opposite the text alignment, so they never push a
    // right-aligned or centered label off its position.
    val indicatorsOnLeft = textAlign == TextAlign.End
    val showBadge = badge > 0 && !blocked

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Blocked (in-focus) apps can't be launched by tapping, but long-press still opens
            // the options sheet so they can be managed.
            .combinedClickable(onClick = { if (!blocked) onClick() }, onLongClick = onLongClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (indicatorsOnLeft) {
            if (isFavorite) { FavStar(blocked); Spacer(Modifier.width(8.dp)) }
            if (showBadge) { NotifBadge(badge); Spacer(Modifier.width(8.dp)) }
            Label(label, fontSizeSp, textAlign, blocked)
        } else {
            Label(label, fontSizeSp, textAlign, blocked)
            if (showBadge) { Spacer(Modifier.width(8.dp)); NotifBadge(badge) }
            if (isFavorite) { Spacer(Modifier.width(8.dp)); FavStar(blocked) }
        }
    }
}

@Composable
private fun RowScope.Label(label: String, fontSizeSp: Int?, textAlign: TextAlign?, blocked: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge.let {
            if (fontSizeSp != null) it.copy(fontSize = fontSizeSp.sp) else it
        },
        color = if (blocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun NotifBadge(count: Int) {
    Text(
        text = if (count > 9) "9+" else count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
private fun FavStar(blocked: Boolean) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (blocked) 0.45f else 1f),
        modifier = Modifier.width(16.dp),
    )
}
