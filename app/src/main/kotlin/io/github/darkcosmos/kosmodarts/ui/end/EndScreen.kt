package io.github.darkcosmos.kosmodarts.ui.end

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.format.averageText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private val CONTENT_MAX_WIDTH = 560.dp
private val MIN_TOUCH_TARGET = 48.dp
private val PADDING = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

/** End of a leg: ranking (ties share a rank), then rematch and new game. */
@Composable
fun EndScreen(game: X01Game, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val rows = endRows(game)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                // Landscape: ranking beside the buttons, so that the buttons stay visible.
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(PADDING)) {
                    ScrollingColumn(Modifier.weight(1f)) { Ranking(rows) }
                    ScrollingColumn(Modifier.weight(1f)) { Actions(onEvent) }
                }
            } else {
                Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
                    ScrollingColumn(Modifier.widthIn(max = CONTENT_MAX_WIDTH).padding(PADDING)) {
                        Ranking(rows)
                        Actions(onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollingColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Ranking(rows: List<EndRow>) {
    rows.firstOrNull { it.isWinner }?.let { winner ->
        Text(
            text = stringResource(R.string.end_winner, winner.name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
    }
    rows.forEach { RankingRow(it) }
}

@Composable
private fun Actions(onEvent: (AppEvent) -> Unit) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .sizeIn(minHeight = MIN_TOUCH_TARGET)
    Button(onClick = { onEvent(AppEvent.Rematch) }, modifier = buttonModifier) {
        Text(stringResource(R.string.end_rematch))
    }
    FilledTonalButton(onClick = { onEvent(AppEvent.NewGame) }, modifier = buttonModifier) {
        Text(stringResource(R.string.home_new_game))
    }
}

@Composable
private fun RankingRow(row: EndRow) {
    Surface(
        color = if (row.isWinner) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.end_rank, row.rank),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.end_average, averageText(row.threeDartAverage)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = pluralStringResource(R.plurals.end_darts_thrown, row.dartsThrown, row.dartsThrown),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview
@Composable
private fun EndScreenPreview() {
    val config = X01Config(listOf("Ann", "Bob"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
    val darts = List(3) { Dart.triple(20) } +
        List(3) { Dart.single(20) } +
        listOf(Dart.triple(20), Dart.triple(20), Dart.single(1))
    KosmoDartsTheme {
        EndScreen(game = X01Game(config, darts), onEvent = {})
    }
}
