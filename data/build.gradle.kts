import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.daggerHiltAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    kotlin("kapt")
    alias(libs.plugins.protobuf)
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
            artifact = "io.github.dogacel:kotlinx-protobuf-gen:0.0.1:jvm8@jar"
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

    // core
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.datetime)

    // worker
    implementation(libs.androidx.work)
    implementation(libs.androidx.work.ktx)

    implementation(libs.androidx.datastore.preferences)

    // hilt di
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.common)
    kapt(libs.hilt.android.compiler)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.client.serialization.kotlinx)

    // serialization
    implementation(libs.kotlinx.serialization)

    // crypto
    implementation(libs.wallet.core)

    // encryption
    implementation(libs.bcprov.jdk15on)

    // other
    implementation(libs.okhttp)
    compileOnly(files("../app/libs/mobile-tss-lib.aar"))
    implementation(libs.timber)
    implementation(libs.spark.core)
    implementation(libs.apache.compress)
    implementation(libs.apache.compress.xz)
    implementation(libs.core.zxing)
    implementation(libs.androidx.security)

    // test
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(kotlin("test"))
}