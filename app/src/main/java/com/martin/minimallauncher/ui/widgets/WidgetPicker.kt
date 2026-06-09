package com.martin.minimallauncher.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.ui.clickableText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPicker(
    controller: WidgetController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    // (widget label, app label, provider) sorted by app then by widget
    val items = remember {
        controller.installedProviders().map { p ->
            val label = p.loadLabel(pm)
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString()
            }.getOrDefault(p.provider.packageName)
            Triple(label, appLabel, p)
        }.sortedWith(compareBy({ it.second.lowercase() }, { it.first.lowercase() }))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                Text(
                    stringResource(R.string.widget_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.widget_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
            items(items) { (label, appLabel, provider) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickableText { controller.startAddWidgetFlow(provider); onDismiss() }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        appLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
