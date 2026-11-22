package com.animehub.tv.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.animehub.tv.data.settings.SettingsRepository
import com.animehub.tv.ui.update.UpdateActions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** En qué va la actualización de la app. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    /** Falta que el usuario permita a AnimeHub TV instalar apps (una sola vez). */
    data class NeedsPermission(val update: AppUpdate, val apk: File) : UpdateState
    data class Installing(val update: AppUpdate, val apk: File) : UpdateState
    data class Failed(val message: String, val update: AppUpdate? = null) : UpdateState
}

/**
 * Actualización desde la propia app: busca la última Release al abrir (y desde Ajustes), descarga
 * el APK y abre el instalador de Android, que la instala encima conservando los datos.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
) : UpdateActions {
    private val checker = UpdateChecker(client, SettingsRepository.DEFAULT_RELEASES_API_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var checkedOnStart = false

    /** Versión instalada (de la propia app). */
    val installedVersion: String by lazy {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"
    }

    /** Al abrir la app: una sola vez, sin avisar si falla. */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        check(quiet = true)
    }

    override fun check() = check(quiet = false)

    fun check(quiet: Boolean) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = runCatching { checker.check(installedVersion) }.fold(
                onSuccess = { update -> update?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate(installedVersion) },
                onFailure = { if (quiet) UpdateState.Idle else UpdateState.Failed("No se pudo buscar la actualización: ${it.message}") },
            )
        }
    }

    /** Descarga la versión nueva y, al terminar, abre el instalador. */
    override fun download(update: AppUpdate) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(update, 0f)
        scope.launch {
            val target = File(updatesDir(), "AnimeHubTV-${update.version}.apk")
            runCatching {
                checker.download(update, target) { p -> _state.value = UpdateState.Downloading(update, p) }
            }.onSuccess { install(update, it) }
                .onFailure { _state.value = UpdateState.Failed("No se pudo descargar la versión ${update.version}: ${it.message}", update) }
        }
    }

    /** Abre el instalador de Android (o pide el permiso para instalar apps la primera vez). */
    fun install(update: AppUpdate, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update, apk)
            return
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        } else {
            Uri.fromFile(apk)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            _state.value = UpdateState.Installing(update, apk)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("Esta TV no tiene instalador de apps disponible", update)
        }
    }

    /** Abre el ajuste "Instalar apps desconocidas" de AnimeHub TV. */
    override fun openInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val specific = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        val general = Intent(Settings.ACTION_SECURITY_SETTINGS)
        return listOf(specific, general).any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }

    override fun install(state: UpdateState.NeedsPermission) = install(state.update, state.apk)

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private fun updatesDir(): File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) File(context.cacheDir, "updates")
        else File(context.getExternalFilesDir(null) ?: context.filesDir, "updates") // file:// en Android 6
}
