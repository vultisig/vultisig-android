plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.daggerHiltAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    kotlin("kapt")
}

android {
    namespace = "com.vulitisig.wallet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {


    implementation(files("libs/mobile-tss-lib.aar"))

    // core
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)

    // hilt di
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)

    // ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.client.serialization.kotlinx)

    // serialization
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization)

    // crypto
    implementation(libs.wallet.core)

    // encryption
    implementation(libs.bcprov.jdk15on)

    // test
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(kotlin("test"))

    //other
    implementation(libs.okhttp)
    implementation(libs.spark.core)
}