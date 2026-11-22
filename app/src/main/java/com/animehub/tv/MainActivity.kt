package com.animehub.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.animehub.tv.remote.TargetDirectory
import com.animehub.tv.remote.TvReceiver
import com.animehub.tv.ui.components.ConfirmDialog
import com.animehub.tv.ui.AdminMode
import com.animehub.tv.ui.LocalAdmin
import com.animehub.tv.ui.isTv
import com.animehub.tv.ui.navigation.AppNavHost
import com.animehub.tv.ui.theme.AnimeHubTheme
import com.animehub.tv.ui.theme.AppColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    /** "Enviar a mi TV": la TV escucha a la tablet; la tablet busca las TVs de la red. */
    @Inject lateinit var receiver: TvReceiver
    /** En la tablet: TVs con AnimeHub, Chromecast y TVs DLNA de la red. */
    @Inject lateinit var targets: TargetDirectory
    @Inject lateinit var adminMode: AdminMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel // Inicia la carga de la configuración.
        setContent {
            val admin by adminMode.enabled.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAdmin provides admin) {
            AnimeHubTheme {
                Box(Modifier.fillMaxSize().background(AppColors.Fondo)) {
                    AppNavHost(onExit = { finish() }, remoteEvents = receiver.bridge.events)
                    // La primera vez que una tablet quiere controlar esta TV, se pregunta.
                    val pending by receiver.pairing.pending.collectAsStateWithLifecycle()
                    pending?.let { request ->
                        ConfirmDialog(
                            title = "¿Permitir “${request.deviceName}”?",
                            message = "Quiere enviar episodios a esta TV y manejar el reproductor (pausa, saltos, siguiente). Solo se pregunta una vez.",
                            confirmText = "Permitir",
                            dismissText = "No permitir",
                            onConfirm = { receiver.pairing.answer(true) },
                            onDismiss = { receiver.pairing.answer(false) },
                        )
                    }
                }
            }
            }
        }
    }

    // Solo mientras la app está a la vista (en segundo plano no se puede abrir el reproductor).
    override fun onStart() {
        super.onStart()
        if (isTv(this)) receiver.start() else targets.start()
    }

    override fun onStop() {
        receiver.stop()
        targets.stop()
        super.onStop()
    }
}
