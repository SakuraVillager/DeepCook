package com.deepcook.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deepcook.app.app.AppViewModel
import com.deepcook.app.app.DeepCookApp
import com.deepcook.app.core.designsystem.DeepCookTheme
import com.deepcook.app.feature.auth.AuthViewModel

class MainActivity : ComponentActivity() {
    private var timerRecipe by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timerRecipe = intent.getStringExtra("timer_recipe")
        enableEdgeToEdge()
        val container = (application as DeepCookApplication).container
        setContent {
            val vm: AppViewModel =
                viewModel(
                    factory = viewModelFactory {
                        initializer {
                            AppViewModel(
                                container.recipes,
                                container.userData,
                                container.userRecipes,
                                container.covers,
                                container.kitchen,
                                container.avatars,
                            )
                        }
                    },
                )
            val state by vm.state.collectAsStateWithLifecycle()
            val auth: AuthViewModel =
                viewModel(
                    factory = viewModelFactory {
                        initializer { AuthViewModel(container.auth) }
                    },
                )
            val authSession by auth.session.collectAsStateWithLifecycle()
            val authForm by auth.form.collectAsStateWithLifecycle()
            DeepCookTheme(state.user.preferences) {
                Surface {
                    DeepCookApp(vm, state, container, auth, authSession, authForm, timerRecipe) {
                        intent.removeExtra("timer_recipe")
                        timerRecipe =
                            null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        timerRecipe = intent.getStringExtra("timer_recipe")
    }
}
