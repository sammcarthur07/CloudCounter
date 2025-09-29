plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlin.kapt)
    id("com.google.gms.google-services") // <<< ADDED: For Firebase
    id("com.google.firebase.appdistribution") // Firebase App Distribution
}

android {
    namespace = "com.vibecode.cloudcounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vibecode.cloudcounter"
        minSdk = 21
        targetSdk = 35
        versionCode = 6
        versionName = "6.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // Enable R8 optimization for smaller APK
            isShrinkResources = true  // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Note: Signing is handled by GitHub Actions for production builds
            // For local builds, this will use debug signing
            signingConfig = signingConfigs.getByName("debug")
            
            // Firebase App Distribution configuration
            firebaseAppDistribution {
                appId = "1:480997623880:android:dcd93d0b88be0fcbdcf07d"
                testers = "vibecode.sam@gmail.com, mcarthur.sp@gmail.com"
                releaseNotes = "Latest build from hybrid workflow"
            }
        }
        debug {
            // Removed applicationIdSuffix to fix Firebase configuration
            // applicationIdSuffix = ".debug"
            // Firebase App Distribution configuration for debug builds
            firebaseAppDistribution {
                appId = "1:480997623880:android:dcd93d0b88be0fcbdcf07d"
                testers = "vibecode.sam@gmail.com, mcarthur.sp@gmail.com"
                releaseNotes = "Debug build from hybrid workflow"
            }
        }
    }
    packagingOptions {
        resources {
            pickFirsts += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/kotlinx-coroutines-core.kotlin_module"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Existing core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // REMOVE THESE TWO LINES - They're causing conflicts:
    // implementation("io.getstream:stream-webrtc-android:1.0.4")
    // implementation("org.webrtc:google-webrtc:1.0.32006")

    //newroomlayout
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")

    // USE ONLY THIS WebRTC implementation:
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // Firebase Realtime Database for WebRTC signaling
    implementation("com.google.firebase:firebase-database-ktx:20.3.0")

    // CameraX for better camera handling (optional but recommended)
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // Coroutines for WebRTC (if not already added)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room Database
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // MPAndroidChart
    implementation(libs.philjay.mpandroidchart)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // ADDED: Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
