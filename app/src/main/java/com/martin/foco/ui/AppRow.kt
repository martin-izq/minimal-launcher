package com.martin.foco.ui

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
import androidx.compose.ui.draw.alpha
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
    val showBadge = badge > 0 && !blocked

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Blocked (in-focus) apps still receive the tap: onClick routes through onAppClick,
            // which shows the focus-block message instead of launching. Long-press opens options.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (textAlign) {
            // Right-aligned: indicators on the left so the label stays flush right.
            TextAlign.End -> {
                if (isFavorite) { FavStar(blocked); Spacer(Modifier.width(8.dp)) }
                if (showBadge) { NotifBadge(badge); Spacer(Modifier.width(8.dp)) }
                Label(label, fontSizeSp, textAlign, blocked)
            }
            // Centered: an invisible mirror of the indicators on the left keeps the label centered.
            TextAlign.Center -> {
                Row(Modifier.alpha(0f), verticalAlignment = Alignment.CenterVertically) {
                    Indicators(showBadge, badge, isFavorite, blocked)
                }
                Label(label, fontSizeSp, textAlign, blocked)
                Indicators(showBadge, badge, isFavorite, blocked)
            }
            // Left-aligned (default): indicators on the right.
            else -> {
                Label(label, fontSizeSp, textAlign, blocked)
                Indicators(showBadge, badge, isFavorite, blocked)
            }
        }
    }
}

/** Trailing indicators (badge + star) with a leading gap; used to the right of a label. */
@Composable
private fun Indicators(showBadge: Boolean, badge: Int, isFavorite: Boolean, blocked: Boolean) {
    if (showBadge) { Spacer(Modifier.width(8.dp)); NotifBadge(badge) }
    if (isFavorite) { Spacer(Modifier.width(8.dp)); FavStar(blocked) }
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
