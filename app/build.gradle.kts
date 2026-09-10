plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.desertstack.aperture"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.desertstack.aperture"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // So the app can keep every Aperture call out of the release build.
        buildConfig = true
    }

    buildTypes {
        release {
            // A real release build shrinks. The sample app does too, so the release variant is
            // checked the way a consumer would actually ship it.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Aperture for debug builds, no-op for release.
    // The sample app builds the modules in this repo, so a change here is testable at once.
    debugImplementation(project(":aperture"))
    releaseImplementation(project(":aperture-no-op"))

    implementation(libs.okhttp)

    // So the sample app has a database and a DataStore for the console to show
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}