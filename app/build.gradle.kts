plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Required for all Firebase services
    id("com.google.gms.google-services")
    // Required for Firebase Crashlytics reporting
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.sheeranergaon.randompicker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sheeranergaon.randompicker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

dependencies {
    // Firebase Bill of Materials (BoM) - ensures library version compatibility
    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))

    // Firebase Analytics (Required for Crashlytics breadcrumbs and research events)
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-messaging")

    // Firebase Crashlytics (For error reporting and project quality proof)
    implementation("com.google.firebase:firebase-crashlytics")

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
    implementation("com.firebaseui:firebase-ui-auth:8.0.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database")

    // AdMob for your Freemium model
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // AndroidX & UI Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}