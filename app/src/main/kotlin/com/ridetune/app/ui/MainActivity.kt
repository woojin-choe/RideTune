package com.ridetune.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
                HomeScreen(
                    profile = vm.profile.collectAsStateWithLifecycle().value,
                    apply = vm.apply.collectAsStateWithLifecycle().value,
                    onLevel = vm::setLevel,
                    onColor = vm::setColor,
                    onTemp = vm::changeTemp,
                    onComfort = vm::applyComfort,
                    onCalm = vm::calm,
                )
            }
        }
    }
}
