package org.mountaineers.traillog.ui.stats

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mountaineers.traillog.R

private val Landowners = listOf(
    "All",
    "Darrington RD",
    "Gifford-Pinchot RD",
    "Snohomish County",
    "Other"
)

/**
 * Stats dashboard in Compose + Material 3.
 * Export CSV uses the selected landowner filter and includes lat/lng.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val exportEvent by viewModel.exportEvent.collectAsStateWithLifecycle()
    var selectedLandowner by remember {
        mutableStateOf(viewModel.getLandownerFilter(context))
    }
    val stats = remember(reports, selectedLandowner) {
        viewModel.computeStatsForFilter(selectedLandowner, reports)
    }

    LaunchedEffect(exportEvent) {
        val event = exportEvent ?: return@LaunchedEffect
        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        viewModel.consumeExportEvent()
    }

    val cards = listOf(
        StatCardUi(stringResource(R.string.stats_label_total), stats.total.toString()),
        StatCardUi(stringResource(R.string.stats_label_cleared), stats.cleared.toString()),
        StatCardUi(stringResource(R.string.stats_label_pending), stats.pending.toString()),
        StatCardUi(stringResource(R.string.stats_label_logs), stats.logsRemoved.toString()),
        StatCardUi(
            stringResource(R.string.stats_label_brush),
            stringResource(R.string.stats_value_feet, stats.brushFeet)
        ),
        StatCardUi(
            stringResource(R.string.stats_label_tread),
            stringResource(R.string.stats_value_feet, stats.treadFeet)
        )
    )

    Scaffold { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.stats_filter_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(Landowners) { owner ->
                            FilterChip(
                                selected = selectedLandowner == owner,
                                onClick = {
                                    selectedLandowner = owner
                                    viewModel.setLandownerFilter(context, owner)
                                },
                                label = { Text(owner) }
                            )
                        }
                    }
                }
            }
            items(cards) { card ->
                StatCard(card)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = { viewModel.exportCsv(context, selectedLandowner) }
                ) {
                    Text(text = stringResource(R.string.export_csv_landowner))
                }
            }
        }
    }
}

private data class StatCardUi(val title: String, val value: String)

@Composable
private fun StatCard(ui: StatCardUi) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = ui.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    text = ui.title,
                    style = MaterialTheme.typography.labelLarge
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
