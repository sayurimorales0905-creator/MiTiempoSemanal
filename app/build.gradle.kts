plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.senati.mitiemposemanal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.senati.mitiemposemanal.smorales"
        minSdk = 24
        targetSdk = 36
        versionCode = 2 // Cambiado de 1 a 2[cite: 7]
        versionName = "0.2.0" // Cambiado de "0.1.0" a "0.2.0"[cite: 7]

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    // Configuración agregada para Desugaring (Fechas modernas en API 24)[cite: 7]
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Dependencia agregada para Desugaring[cite: 7]
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}