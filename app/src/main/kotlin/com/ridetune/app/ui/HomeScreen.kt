package com.ridetune.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ridetune.app.R
import com.ridetune.app.data.SenseArea
import com.ridetune.app.data.level
import com.ridetune.app.data.vehicle.CabinItem
import com.ridetune.app.data.vehicle.ItemResult
import com.ridetune.app.data.vehicle.Outcome
import com.ridetune.app.domain.CalmColor
import com.ridetune.app.domain.Sensitivity
import com.ridetune.app.domain.SensoryProfile

/** 왼쪽: 감각 프로필 편집 / 오른쪽: 적용·진정 모드. 차량 가로 화면 기준. */
@Composable
fun HomeScreen(
    profile: SensoryProfile,
    apply: ApplyState,
    onLevel: (SenseArea, Sensitivity) -> Unit,
    onColor: (CalmColor) -> Unit,
    onTemp: (Float) -> Unit,
    onComfort: () -> Unit,
    onCalm: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ProfilePanel(profile, onLevel, onColor, onTemp, Modifier.weight(1.4f).fillMaxHeight())
            ActionPanel(apply, onComfort, onCalm, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ProfilePanel(
    profile: SensoryProfile,
    onLevel: (SenseArea, Sensitivity) -> Unit,
    onColor: (CalmColor) -> Unit,
    onTemp: (Float) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
        SenseArea.entries.forEach { area ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(area.label), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(220.dp))
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    Sensitivity.entries.forEachIndexed { i, level ->
                        SegmentedButton(
                            selected = profile.level(area) == level,
                            onClick = { onLevel(area, level) },
                            shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                            modifier = Modifier.height(56.dp),
                        ) { Text(stringResource(level.label), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_temp), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(220.dp))
            OutlinedButton(onClick = { onTemp(-0.5f) }, modifier = Modifier.size(56.dp)) { Text("−") }
            Text(
                stringResource(R.string.temp_value, profile.preferredTempC),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedButton(onClick = { onTemp(0.5f) }, modifier = Modifier.size(56.dp)) { Text("+") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_color), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(220.dp))
            CalmColor.entries.forEach { color ->
                val selected = profile.calmColor == color
                Box(
                    Modifier
                        .padding(end = 16.dp)
                        .size(56.dp)
                        .background(Color(color.argb), CircleShape)
                        .border(if (selected) 4.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onColor(color) },
                )
            }
        }
    }
}

@Composable
private fun ActionPanel(apply: ApplyState, onComfort: () -> Unit, onCalm: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Button(
            onClick = onComfort,
            enabled = apply.running == null,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) { Text(stringResource(R.string.action_comfort), style = MaterialTheme.typography.headlineSmall) }
        Button(
            onClick = onCalm,
            enabled = apply.running == null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) { Text(stringResource(R.string.action_calm), style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.height(8.dp))
        when {
            apply.running != null -> Text(stringResource(R.string.applying), style = MaterialTheme.typography.titleMedium)
            apply.report != null && apply.report.results.isEmpty() ->
                Text(stringResource(R.string.nothing_to_apply), style = MaterialTheme.typography.titleMedium)
            apply.report != null -> {
                Text(
                    stringResource(R.string.report_title, apply.report.okCount, apply.report.results.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                apply.report.results.forEach { ResultRow(it) }
            }
        }
    }
}

@Composable
private fun ResultRow(result: ItemResult) {
    val outcome = stringResource(
        when (result.outcome) {
            Outcome.OK -> R.string.outcome_ok
            Outcome.UNSUPPORTED -> R.string.outcome_unsupported
            Outcome.FAILED -> R.string.outcome_failed
        },
    )
    Text("${stringResource(result.item.label)} · $outcome", style = MaterialTheme.typography.bodyLarge)
}

@get:StringRes
private val SenseArea.label
    get() = when (this) {
        SenseArea.SOUND -> R.string.sense_sound
        SenseArea.LIGHT -> R.string.sense_light
        SenseArea.CLIMATE -> R.string.sense_climate
        SenseArea.CHANGE -> R.string.sense_change
    }

@get:StringRes
private val Sensitivity.label
    get() = when (this) {
        Sensitivity.NONE -> R.string.level_none
        Sensitivity.SOME -> R.string.level_some
        Sensitivity.HIGH -> R.string.level_high
    }

@get:StringRes
private val CabinItem.label
    get() = when (this) {
        CabinItem.AMBIENT_BRIGHTNESS -> R.string.item_ambient_brightness
        CabinItem.AMBIENT_COLOR -> R.string.item_ambient_color
        CabinItem.CABIN_LIGHT -> R.string.item_cabin_light
        CabinItem.SMART_GLASS -> R.string.item_smart_glass
        CabinItem.TEMPERATURE -> R.string.item_temperature
        CabinItem.FAN_SPEED -> R.string.item_fan_speed
        CabinItem.RECIRCULATION -> R.string.item_recirculation
        CabinItem.WINDOWS -> R.string.item_windows
    }

@Preview(widthDp = 1920, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        HomeScreen(
            profile = SensoryProfile(sound = Sensitivity.HIGH, light = Sensitivity.SOME),
            apply = ApplyState(),
            onLevel = { _, _ -> }, onColor = {}, onTemp = {}, onComfort = {}, onCalm = {},
        )
    }
}
