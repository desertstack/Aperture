plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.aperture"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

}

mavenPublishing {
    publishToMavenCentral()

    // Coordinates and POM metadata come from gradle.properties:
    // GROUP / VERSION_NAME / POM_* in the root, POM_ARTIFACT_ID + POM_NAME +
    // POM_DESCRIPTION in this module. Do not repeat them in a pom {} block here:
    // licenses and developers are collections, so they would be appended twice.

    // Signing needs a GPG key. Skipped when absent so publishToMavenLocal works
    // on a fresh machine; Central rejects unsigned uploads regardless.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.status.pages)
    // Note: SSE support is built into ktor-server-core

    // OkHttp (compileOnly - provided by consumer)
    compileOnly(libs.okhttp)

    // DataStore (compileOnly - only apps that use DataStore have it at runtime)
    compileOnly(libs.datastore.preferences)
    testImplementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp)
    testImplementation(libs.ktor.server.test.host)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
