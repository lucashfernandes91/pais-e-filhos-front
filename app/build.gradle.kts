import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Carrega local.properties (gitignored, por dev). Fallback se ausente.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.chatapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.chatapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Host do backend em dev. Sobrescreva em local.properties:
        //   dev.host=localhost       (celular físico via adb reverse)
        //   dev.host=192.168.x.x     (celular na mesma WiFi)
        // Default = 10.0.2.2 (emulador).
        val devHost = localProps.getProperty("dev.host", "10.0.2.2")
        val devPort = localProps.getProperty("dev.port", "8000")
        buildConfigField("String", "API_BASE_URL", "\"http://$devHost:$devPort/\"")
        buildConfigField("String", "WS_BASE_URL", "\"ws://$devHost:$devPort/\"")
    }

    buildTypes {
        debug {
            val mockUsername = localProps.getProperty("dev.username", "pai_demo")
            val mockPassword = localProps.getProperty("dev.password", "PaisEFilhos!2026")
            buildConfigField("boolean", "MOCK_LOGIN_ENABLED", "true")
            buildConfigField("String", "MOCK_USERNAME", "\"$mockUsername\"")
            buildConfigField("String", "MOCK_PASSWORD", "\"$mockPassword\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "MOCK_LOGIN_ENABLED", "false")
            buildConfigField("String", "MOCK_USERNAME", "\"\"")
            buildConfigField("String", "MOCK_PASSWORD", "\"\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation(platform("com.google.firebase:firebase-bom:34.14.1"))
    implementation("com.google.firebase:firebase-messaging")
}
