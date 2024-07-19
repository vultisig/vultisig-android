import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    kotlin("kapt")
    alias(libs.plugins.daggerHiltAndroid)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.protobuf)
}
android {
    namespace = "com.vultisig.wallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vultisig.wallet"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*.md"
        }
    }

    sourceSets.getByName("main") {
        proto {
            srcDir("${project.rootProject.rootDir}/commondata/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.23.4"
    }

    plugins {
        id("kotlinx-protobuf-gen") {
            artifact = "io.github.dogacel:kotlinx-protobuf-gen:alpha-SNAPSHOT:jvm8@jar"
        }
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("kotlinx-protobuf-gen") {}
            }
        }
    }
}

dependencies {
    implementation(files("libs/mobile-tss-lib.aar"))
    implementation(libs.bcprov.jdk15on)

    // kotlinx
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)

    // androidx
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)

    // compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // camera
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // hilt di
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)

    // ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)

    // other
    implementation(libs.accompanist.permissions)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.mlkit.barcode)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.spark.core)
    implementation(libs.core.zxing)
    implementation(libs.wallet.core)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coil)

    // test
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.junit.jupiter)

}
