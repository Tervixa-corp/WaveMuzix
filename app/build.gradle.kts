import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.wavemuzix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.wavemuzix"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1 (trial)"

        // Load SendGrid API Key from local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val stream = FileInputStream(localPropertiesFile)
            properties.load(stream)
            stream.close()
        }
        val sendGridKey = properties.getProperty("SENDGRID_API_KEY") ?: ""
        buildConfigField("String", "SENDGRID_API_KEY", "\"$sendGridKey\"")
        val gmailAppPassword = properties.getProperty("GMAIL_APP_PASSWORD") ?: ""
        buildConfigField("String", "GMAIL_APP_PASSWORD", "\"$gmailAppPassword\"")
        val gmailSenderEmail = properties.getProperty("GMAIL_SENDER_EMAIL") ?: "wavemuzix@gmail.com"
        buildConfigField("String", "GMAIL_SENDER_EMAIL", "\"$gmailSenderEmail\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks.add("release")
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles("benchmark-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    /* configurations.all {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
        exclude(group = "androidx.compose.runtime", module = "runtime-annotation")
        exclude(group = "androidx.compose.runtime", module = "runtime-annotation-android")
    } */
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi")
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

// Fix for KSP unexpected jvm signature V
project.extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
    arg("ksp.kotlin.skipMetadataVersionCheck", "true")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose - Version Alignment via BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation("androidx.compose.runtime:runtime-saveable")
    
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Media3
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.security.crypto)

    // Network & Images
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation("com.google.protobuf:protobuf-javalite:4.26.1") // Explicitly include version needed for Firestore
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.glide.compose)
    implementation(libs.androidx.palette)
    
    // UI Libraries
    implementation(libs.haze)
    implementation(libs.liquidglass)
    
    // Material Motion
    implementation("io.github.fornewid:material-motion-compose-core:2.0.1")

    // Mailing
    implementation(libs.mail.android)
    implementation(libs.activation.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Work Manager
    implementation(libs.androidx.work.runtime)
    implementation(libs.mp3agic)
    implementation(libs.androidx.profileinstaller)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.database)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.canhub.cropper)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
