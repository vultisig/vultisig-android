import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.daggerHiltAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.vultisig.wallet"
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
    sourceSets.getByName("main") {
        proto {
            srcDir("${project.rootProject.rootDir}/commondata/proto")
        }
    }

    testOptions {
        unitTests.all { task -> // 'task' here refers to the specific Test task (e.g., testDebugUnitTest)
            // Print task name to ensure this block is executed for the test task
            println("--- Debugging Wallet-Core Local Unit Tests ---")
            println("Configuring unit test task: ${task.name}")

            val moduleBuildDir = project.layout.buildDirectory.get().asFile.absolutePath

            // Get the JVM's architecture for the test run
            val jvmArch = System.getProperty("os.arch").lowercase()
            println("Detected JVM Architecture: $jvmArch")

            // Map common JVM architectures to Android ABIs found in intermediates
            // This mapping is crucial!
            val abiToTest = when (jvmArch) {
                "x86_64", "amd64" -> "x86_64" // Common for Intel/AMD 64-bit machines
                "aarch64" -> "arm64-v8a" // Common for ARM-based machines (e.g., Apple Silicon Macs running native ARM JVM)
                "x86" -> "x86"
                "arm" -> "armeabi-v7a" // Less common for dev machines, but good to include
                else -> {
                    println("WARNING: Unrecognized JVM architecture '$jvmArch'. Defaulting to x86_64.")
                    "x86_64" // Fallback
                }
            }

            // *** DIRECTLY USE YOUR KNOWN DIRECTORY ***
            val knownBaseLibDir = file("$moduleBuildDir/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib")

            // Construct the full path to the ABI-specific native library directory
            val abiSpecificPath = File(knownBaseLibDir, abiToTest)

            if (abiSpecificPath.exists() && abiSpecificPath.isDirectory) {
                // *** CORRECT WAY TO SET SYSTEM PROPERTIES ***
                task.systemProperties.put("java.library.path", abiSpecificPath.absolutePath)

                println("Found native library directory: ${abiSpecificPath.absolutePath}")
                println("SUCCESS: java.library.path set to: ${abiSpecificPath.absolutePath}")
            } else {
                println("ERROR: Native library directory NOT found for ABI '$abiToTest' at expected path: ${abiSpecificPath.absolutePath}")
                println("Local unit tests for Wallet-Core functionality WILL LIKELY FAIL with UnsatisfiedLinkError.")
                println("Please ensure Wallet-Core dependency is correctly configured and rebuild, then verify this exact path manually.")
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
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
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)
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
    implementation(libs.web3)

    // test
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(libs.wallet.core)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.wallet.core)
}

