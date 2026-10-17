package com.deepcook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deepcook.app.app.AppViewModel
import com.deepcook.app.app.DeepCookApp
import com.deepcook.app.core.designsystem.DeepCookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DeepCookApplication).container
        setContent {
            val vm: AppViewModel =
                viewModel(
                    factory = viewModelFactory {
                        initializer {
                            AppViewModel(container.recipes, container.userData, container.userRecipes, container.covers)
                        }
                    },
                )
            val state by vm.state.collectAsStateWithLifecycle()
            DeepCookTheme(state.user.preferences) {
                Surface { DeepCookApp(vm, state, container) }
            }
        }
    }
}
