plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
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

        buildConfigField("String", "API_BASE_URL", "\"https://coparent-hml.originstudios.com.br/\"")
        buildConfigField("String", "WS_BASE_URL", "\"wss://coparent-hml.originstudios.com.br/\"")
        buildConfigField("String", "LEGAL_BASE_URL", "\"https://coparent-hml.originstudios.com.br\"")
        buildConfigField("boolean", "FIREBASE_MESSAGING_ENABLED", "true")
        manifestPlaceholders["appLinkHost"] = "coparent-hml.originstudios.com.br"
        manifestPlaceholders["firebaseMessagingAutoInitEnabled"] = "true"
        manifestPlaceholders["firebaseAnalyticsCollectionEnabled"] = "true"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "MOCK_LOGIN_ENABLED", "false")
            buildConfigField("String", "MOCK_USERNAME", "\"\"")
            buildConfigField("String", "MOCK_PASSWORD", "\"\"")
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
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-hml"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")

            buildConfigField("String", "API_BASE_URL", "\"https://coparent-hml.originstudios.com.br/\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://coparent-hml.originstudios.com.br/\"")
            buildConfigField("String", "LEGAL_BASE_URL", "\"https://coparent-hml.originstudios.com.br\"")
            buildConfigField("boolean", "FIREBASE_MESSAGING_ENABLED", "true")
            buildConfigField("boolean", "MOCK_LOGIN_ENABLED", "false")
            buildConfigField("String", "MOCK_USERNAME", "\"\"")
            buildConfigField("String", "MOCK_PASSWORD", "\"\"")
            manifestPlaceholders["appLinkHost"] = "coparent-hml.originstudios.com.br"
            manifestPlaceholders["firebaseMessagingAutoInitEnabled"] = "true"
            manifestPlaceholders["firebaseAnalyticsCollectionEnabled"] = "false"
            resValue("string", "app_name", "CoParent Homologação")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
}

tasks.matching { it.name == "processStagingGoogleServices" }.configureEach {
    doFirst {
        check(file("src/staging/google-services.json").isFile) {
            "A variante staging exige app/src/staging/google-services.json de um projeto Firebase exclusivo de homologação."
        }
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
