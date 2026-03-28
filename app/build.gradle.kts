plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    // Add this if using KSP (recommended for Room/Hilt):
    id("com.google.devtools.ksp") version "2.3.6" // adjust to match your Kotlin version
}

android {
    namespace = "org.mountaineers.traillog"
    compileSdk = 36   // Updated to latest (Android 16)

    defaultConfig {
        applicationId = "org.mountaineers.traillog"
        minSdk = 24
        targetSdk = 36   // Updated — aligns with compileSdk, better for Play Store compliance
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // If using Compose later, add:
        // vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // If you plan to migrate to Jetpack Compose, enable it here:
    // buildFeatures {
    //     compose = true
    // }
    // composeOptions {
    //     kotlinCompilerExtensionVersion = "1.5.17"  // or latest matching Kotlin
    // }

    // If still using view binding (XML), keep; otherwise remove
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core & UI libs (updated where newer stable exists)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Hilt (latest stable)
    implementation("com.google.dagger:hilt-android:2.59.2")

    ksp("com.google.dagger:hilt-compiler:2.59.2")   // Use KSP instead of kapt for Hilt

    // OSMDroid (still latest as of mid-2025; no major updates visible)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Glide
    implementation("com.github.bumptech.glide:glide:5.0.5")
    // If using annotations: ksp("com.github.bumptech.glide:ksp:5.0.5") // optional

    // Firebase (BOM keeps versions aligned)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Room (updated, switched to KSP for compiler)
    val roomVersion = "2.8.4"   // Still latest in 2.x series; Room 3.0-alpha exists but breaking/KMP-focused
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")   // Recommended over annotationProcessor

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.1")

    // Test libs (add if needed)
    // testImplementation("junit:junit:4.13.2")
    // androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}