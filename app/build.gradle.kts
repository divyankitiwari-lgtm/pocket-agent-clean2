plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.pocketagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pocketagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.2"
    }

    buildFeatures { viewBinding = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}


// Firebase services are applied only after the project owner adds the matching
// app/google-services.json file. The app remains buildable before configuration.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
