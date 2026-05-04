plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.daggerHiltAndroid)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ktfmt)
}

ktfmt { kotlinLangStyle() }

android {
    namespace = "com.vultisig.wallet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vultisig.wallet"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 35
        versionCode = 101
        versionName = "1.0.101"

        testInstrumentationRunner = "com.vultisig.wallet.util.HiltTestRunner"

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*.md"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs { keepDebugSymbols += "**/*.so" }
    }
    tasks.withType<Test> { useJUnitPlatform() }
    lint {
        abortOnError = true
        absolutePaths = false
        lintConfig = file("$rootDir/config/lint/lint.xml")
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":data"))

    implementation(files("libs/mobile-tss-lib.aar"))
    implementation(files("libs/dkls-release.aar"))
    implementation(files("libs/goschnorr-release.aar"))
    implementation(files("libs/dilithium-release.aar"))

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
    implementation(libs.androidx.browser)

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
    implementation(libs.firebase.messaging)
    implementation(libs.lifecycle.process)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core.ktx)

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
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.work)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    // ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.client.serialization.kotlinx)

    // other
    implementation(libs.accompanist.permissions)
    implementation(libs.apache.compress)
    implementation(libs.apache.compress.xz)
    implementation(libs.mlkit.barcode)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.spark.core)
    implementation(libs.core.zxing)
    implementation(libs.wallet.core)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coil)
    implementation(libs.play.update)
    implementation(libs.play.review)
    implementation(libs.androidx.work.ktx)
    implementation(libs.bcprov.jdk18on)

    // animation
    implementation(libs.lottie.compose)
    implementation(libs.rive)

    // test
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.wallet.core)
    androidTestImplementation(libs.ktor.client.mock)
    testImplementation(kotlin("test"))
}
