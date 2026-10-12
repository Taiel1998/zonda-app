plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP genera el código de Room (DAOs y base) en tiempo de compilación.
    alias(libs.plugins.ksp)
}

android {
    namespace = "ar.edu.uade.zonda"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ar.edu.uade.zonda"
        // Android 8.0 (API 26) o superior, según RNF11.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric necesita los recursos de Android para correr Room en memoria en la JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    // El dominio expone Flow y funciones suspend; coroutines-core no depende de Android.
    implementation(libs.kotlinx.coroutines.core)
    // Caché local: Room como fuente de verdad, cifrada con SQLCipher (RNF01 y RNF03).
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    // Preferencias del teléfono (avisos, primer inicio).
    implementation(libs.androidx.datastore.preferences)
    // Red: Retrofit sobre OkHttp y Gson para el JSON del contrato (docs/decisiones.md, decisión 12).
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    // Escanear el QR de pareo: CameraX para la cámara y ML Kit (modelo incluido en la app) para leer el código.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    // Sincronización en segundo plano cada 15 minutos con red (RNF08).
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Room en memoria sobre la JVM, sin emulador.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    // Servidor HTTP de mentira para probar el cliente: cabeceras, códigos de error y tiempos de espera.
    testImplementation(libs.okhttp.mockwebserver)
    // Pantallas de Compose dibujadas sobre Robolectric: se verifica lo que se muestra y lo que pasa al tocar.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Ejecutar el worker de sincronización en una prueba, sin WorkManager real.
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
