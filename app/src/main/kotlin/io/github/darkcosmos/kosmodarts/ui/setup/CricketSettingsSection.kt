package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.SegmentedChoice
import io.github.darkcosmos.kosmodarts.ui.components.SettingsSwitch
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The Cricket settings of the setup (CR21): "VARIANT" with Standard / Cut-throat and one hint line for the selected
 * variant in `onBackgroundMuted`, then the "25-Bull" switch, on by default.
 */
@Composable
internal fun CricketSettingsSection(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    SectionLabel(stringResource(R.string.setup_cricket_variant_label))
    SegmentedChoice(
        options = CricketVariant.entries,
        selected = setup.cricketVariant,
        label = { variantName(it) },
        onSelect = { onEvent(AppEvent.CricketVariantSelected(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(
            when (setup.cricketVariant) {
                CricketVariant.STANDARD -> R.string.setup_cricket_hint_standard
                CricketVariant.CUT_THROAT -> R.string.setup_cricket_hint_cut_throat
            },
        ),
        style = KosmoTheme.typography.body,
        color = KosmoTheme.colors.onBackgroundMuted,
        modifier = Modifier.testTag(SetupTags.CRICKET_HINT),
    )
    SettingsSwitch(
        label = stringResource(R.string.setup_cricket_bull),
        checked = setup.isBullInPlay,
        tag = SetupTags.CRICKET_BULL,
        onToggle = { onEvent(AppEvent.BullInPlayToggled(it)) },
    )
}

@Composable
private fun variantName(variant: CricketVariant): String = stringResource(
    when (variant) {
        CricketVariant.STANDARD -> R.string.cricket_variant_standard
        CricketVariant.CUT_THROAT -> R.string.cricket_variant_cut_throat
    },
)
