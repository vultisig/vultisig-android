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
    namespace = "com.vultisig.wallet.data"
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
            artifact =
                "io.github.dogacel:kotlinx-protobuf-gen:${libs.versions.kotlinxProtobufGen.get()}:jvm8@jar"
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
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        )
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

    // test
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.wallet.core)

    implementation(platform(libs.ethers.bom)) {
        exclude(
            group = "org.bouncycastle",
            module = "bcprov-jdk18on"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-slf4j2-impl"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-core"
        )
        exclude(
            group = "org.bouncycastle",
            module = "bcprov-jdk15to18"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-api"
        )
    }
    implementation(libs.ethers.abi) {
        exclude(
            group = "org.bouncycastle",
            module = "bcprov-jdk18on"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-slf4j2-impl"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-core"
        )
        exclude(
            group = "org.apache.logging.log4j",
            module = "log4j-api"
        )
        exclude(
            group = "org.bouncycastle",
            module = "bcprov-jdk15to18"
        )
    }
}