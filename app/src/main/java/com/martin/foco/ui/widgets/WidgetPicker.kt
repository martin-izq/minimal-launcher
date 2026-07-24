package com.martin.foco.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.martin.foco.R
import com.martin.foco.ui.ScreenHeader
import com.martin.foco.ui.clickableText
import com.martin.foco.ui.stableStatusBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A provider as shown in the picker: its own label plus the app it belongs to. */
private data class WidgetEntry(
    val widgetLabel: String,
    val appLabel: String,
    val provider: AppWidgetProviderInfo,
)

/**
 * Full-screen widget picker overlay: a back chevron + title ([ScreenHeader]) over a searchable
 * list of installed providers with their previews, matching the Settings / Screen Time screens.
 * Back or the chevron dismisses it.
 */
@Composable
fun WidgetPicker(
    controller: WidgetController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // Reading every provider's label goes through PackageManager once per widget, which is far
    // too slow for the composition thread: doing it inline froze the screen on open.
    // null = still loading.
    val entries by produceState<List<WidgetEntry>?>(initialValue = null, controller) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            controller.installedProviders().map { provider ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0))
                        .toString()
                }.getOrDefault(provider.provider.packageName)
                WidgetEntry(
                    widgetLabel = runCatching { provider.loadLabel(pm) }
                        .getOrDefault(provider.provider.shortClassName),
                    appLabel = appLabel,
                    provider = provider,
                )
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
        }
    }

    BackHandler(onBack = onDismiss)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .stableStatusBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(stringResource(R.string.widget_picker_title), onDismiss)
        }

        val loaded = entries
        if (loaded == null) {
            PickerMessage(stringResource(R.string.widget_picker_loading))
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.widget_picker_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )

        val filtered = remember(loaded, query) {
            val q = query.trim()
            if (q.isEmpty()) {
                loaded
            } else {
                loaded.filter {
                    it.widgetLabel.contains(q, ignoreCase = true) ||
                        it.appLabel.contains(q, ignoreCase = true)
                }
            }
        }

        if (filtered.isEmpty()) {
            PickerMessage(stringResource(R.string.widget_picker_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { entry ->
                    WidgetEntryRow(entry) {
                        controller.startAddWidgetFlow(entry.provider)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
    )
}

@Composable
private fun WidgetEntryRow(entry: WidgetEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableText(onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPreview(entry.provider)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                entry.widgetLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                entry.appLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The provider's preview image, loaded per row so a slow or missing preview never blocks the
 * list. Falls back to the provider's icon, and to an empty placeholder when neither loads.
 */
@Composable
private fun WidgetPreview(provider: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, provider) {
        value = withContext(Dispatchers.IO) {
            val densityDpi = context.resources.displayMetrics.densityDpi
            val drawable = runCatching { provider.loadPreviewImage(context, densityDpi) }.getOrNull()
                ?: runCatching { provider.loadIcon(context, densityDpi) }.getOrNull()
            drawable?.toPreviewBitmap()?.asImageBitmap()
        }
    }

    Box(
        Modifier
            .size(width = 72.dp, height = 56.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

// Previews ship at full widget resolution; a row only ever draws a thumbnail of them.
private const val PREVIEW_MAX_PX = 240

private fun Drawable.toPreviewBitmap(): Bitmap? {
    val w = intrinsicWidth.takeIf { it > 0 } ?: PREVIEW_MAX_PX
    val h = intrinsicHeight.takeIf { it > 0 } ?: PREVIEW_MAX_PX
    val scale = (PREVIEW_MAX_PX.toFloat() / maxOf(w, h)).coerceAtMost(1f)
    return runCatching {
        toBitmap(
            width = (w * scale).roundToInt().coerceAtLeast(1),
            height = (h * scale).roundToInt().coerceAtLeast(1),
        )
    }.getOrNull()
}
