// Archivo de compilación raíz: solo declara los plugins (se aplican en :app).
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 integra Kotlin; declararlo aquí solo fija la versión de Kotlin usada.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
