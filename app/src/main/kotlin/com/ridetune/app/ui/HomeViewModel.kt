package com.ridetune.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ridetune.app.RideTuneApp
import com.ridetune.app.data.ProfileRepository
import com.ridetune.app.data.SenseArea
import com.ridetune.app.data.vehicle.ApplyReport
import com.ridetune.app.data.vehicle.CabinController
import com.ridetune.app.data.withLevel
import com.ridetune.app.domain.CabinPlanner
import com.ridetune.app.domain.CalmColor
import com.ridetune.app.domain.Sensitivity
import com.ridetune.app.domain.SensoryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY

enum class Mode { COMFORT, CALM }

data class ApplyState(val running: Mode? = null, val last: Mode? = null, val report: ApplyReport? = null)

class HomeViewModel(
    private val profiles: ProfileRepository,
    private val cabin: CabinController,
) : ViewModel() {

    val profile: StateFlow<SensoryProfile> = profiles.profile

    private val _apply = MutableStateFlow(ApplyState())
    val apply: StateFlow<ApplyState> = _apply.asStateFlow()

    fun setLevel(area: SenseArea, level: Sensitivity) = profiles.save(profile.value.withLevel(area, level))

    fun setColor(color: CalmColor) = profiles.save(profile.value.copy(calmColor = color))

    fun changeTemp(deltaC: Float) =
        profiles.save(profile.value.copy(preferredTempC = (profile.value.preferredTempC + deltaC).coerceIn(16f, 30f)))

    fun applyComfort() = run(Mode.COMFORT)

    fun calm() = run(Mode.CALM)

    private fun run(mode: Mode) {
        if (_apply.value.running != null) return
        _apply.update { it.copy(running = mode) }
        viewModelScope.launch {
            val target = when (mode) {
                Mode.COMFORT -> CabinPlanner.comfort(profile.value)
                Mode.CALM -> CabinPlanner.calm(profile.value)
            }
            val report = cabin.apply(target)
            _apply.value = ApplyState(running = null, last = mode, report = report)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RideTuneApp
                HomeViewModel(app.profiles, app.cabin)
            }
        }
    }
}
