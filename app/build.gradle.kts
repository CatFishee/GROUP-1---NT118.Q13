plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.metube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.metube"
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    // Required for JavaMail to avoid duplicate file errors
    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    // --- Core AndroidX ---
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.circleimageview)

    // --- Glide ---
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // --- Google Sign-In ---
    implementation(libs.play.services.auth)

    // --- Facebook Login ---
    implementation(libs.facebook.login)

    // --- Credential Manager ---
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // --- Email Verification (JavaMail) ---
    implementation(libs.android.mail)
    implementation(libs.android.activation)

    // --- Cloudinary ---
    implementation(libs.cloudinary.android)

    // --- Supabase ---
    implementation(libs.supabase.storage)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.okhttp)

    // --- Kotlinx Serialization ---
    implementation(libs.kotlinx.serialization.json)

    // --- ExoPlayer ---
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.palette:palette:1.0.0")

    // --- MPAndroidChart ---
    implementation(libs.mpandroidchart)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}