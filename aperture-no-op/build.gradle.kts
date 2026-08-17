plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.aperture.noop"
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
    // OkHttp (compileOnly - provided by consumer)
    compileOnly(libs.okhttp)

    // Testing
    testImplementation(libs.junit)
}
