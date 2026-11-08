package com.animehub.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.animehub.tv.ui.navigation.AppNavHost
import com.animehub.tv.ui.theme.AnimeHubTheme
import com.animehub.tv.ui.theme.AppColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel // Inicia la carga de la configuración.
        setContent {
            AnimeHubTheme {
                Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
                    AppNavHost(onExit = { finish() })
                }
            }
        }
    }
}
