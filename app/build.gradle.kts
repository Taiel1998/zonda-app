import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP genera el código de Room (DAOs y base) en tiempo de compilación.
    alias(libs.plugins.ksp)
}

// Firma del release (decisión 28). La clave y su contraseña viven fuera del repositorio: la propiedad
// zonda.firma, en ~/.gradle/gradle.properties, apunta a un archivo con archivo, alias y contrasenia.
// Sin esa propiedad (el CI, otra máquina) el release sale sin firmar.
val archivoFirma = providers.gradleProperty("zonda.firma").orNull?.let { file(it) }
val firma = archivoFirma?.takeIf { it.isFile }?.let { archivo ->
    Properties().apply { archivo.inputStream().use { load(it) } }
}
if (archivoFirma != null && firma == null) {
    logger.warn("zonda.firma apunta a $archivoFirma, que no existe: el release sale sin firmar.")
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
        versionCode = 7
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (archivoFirma != null && firma != null) {
            create("release") {
                storeFile = archivoFirma.resolveSibling(firma.getProperty("archivo"))
                storePassword = firma.getProperty("contrasenia")
                keyAlias = firma.getProperty("alias")
                keyPassword = firma.getProperty("contrasenia")
            }
        }
    }

    buildTypes {
        release {
            // Con la clave de la decisión 28; si no está, queda null y el APK sale sin firmar.
            signingConfig = signingConfigs.findByName("release")
            // Sin R8: Gson arma los DTO por reflexión y achicar el APK pediría reglas propias (decisión 28).
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
    lint {
        // Una llamada que necesita un Android más nuevo que minSdk frena el APK de release (el lint de
        // assembleRelease) en lugar de cerrar la app en esos teléfonos. Las pruebas corren en la JVM y el
        // emulador tiene Android 16, así que ninguno de los dos lo ve: pasó con la 1.0.5 en el moto (Android 11).
        fatal += "NewApi"
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
    implementation(libs.androidx.compose.material.icons.extended)
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
    // Dibuja el QR para sumar a una persona en el teléfono del administrador (decisión 39).
    implementation(libs.zxing.core)
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
