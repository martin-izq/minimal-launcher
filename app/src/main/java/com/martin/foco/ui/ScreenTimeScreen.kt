package com.martin.foco.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.martin.foco.LauncherUiState
import com.martin.foco.R
import com.martin.foco.util.formatDuration
import com.martin.foco.util.formatDurationShort

@Composable
fun ScreenTimeScreen(
    state: LauncherUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val usage = state.usage

    Column(
        Modifier
            .fillMaxSize()
            .stableStatusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        ScreenHeader(stringResource(R.string.screentime_title), onBack)

        if (!usage.hasPermission) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.screentime_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text(stringResource(R.string.screentime_grant)) }
            return@Column
        }

        val ranking = usage.perAppToday.entries
            .sortedByDescending { it.value }
            .map { (pkg, ms) ->
                val label = state.allApps.firstOrNull { it.packageName == pkg }
                    ?.displayLabel(state.settings.renames) ?: pkg
                label to ms
            }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    formatDuration(usage.totalTodayMs),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.screentime_today_unlocks, usage.unlocksToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Spacer(Modifier.height(24.dp))
                WeeklyChart(state)

                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.screentime_per_app),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (ranking.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.screentime_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }

            items(ranking) { (label, ms) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatDuration(ms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun WeeklyChart(state: LauncherUiState) {
    val data = state.usage.weekly
    if (data.isEmpty()) return
    val max = (data.maxOfOrNull { it.totalMs } ?: 1L).coerceAtLeast(1L)
    val chartHeight = 120.dp

    Row(
        Modifier.fillMaxWidth().height(chartHeight + 36.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { day ->
            val frac = (day.totalMs.toFloat() / max.toFloat()).coerceIn(0.02f, 1f)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    formatDurationShort(day.totalMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(chartHeight * frac)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
