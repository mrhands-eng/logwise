package ru.astondev.tempomobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.domain.AppSettings
import ru.astondev.tempomobile.ui.TempoNavHost
import ru.astondev.tempomobile.ui.theme.TempoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by userPreferences.settings.collectAsStateWithLifecycle(AppSettings())
            TempoTheme(
                themeMode = settings.themeMode,
                palette = settings.palette,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TempoNavHost()
                }
            }
        }
    }
}
