import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.daggerHiltAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ktfmt)
}

ktfmt { kotlinLangStyle() }

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
                "proguard-rules.pro",
            )
        }
    }
    sourceSets.getByName("main") {
        proto { srcDir("${project.rootProject.rootDir}/commondata/proto") }
    }
    packaging {
        resources {
            // bcprov and jspecify both ship this file, which collides when the instrumented-test
            // APK is assembled. Mirrors the exclude :app already carries.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    lint {
        abortOnError = true
        absolutePaths = false
        lintConfig = file("$rootDir/config/lint/lint.xml")
        baseline = file("lint-baseline.xml")
    }
}

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnitPlatform() }

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.23.4" }

    plugins {
        id("kotlinx-protobuf-gen") {
            artifact =
                "io.github.dogacel:kotlinx-protobuf-gen:${libs.versions.kotlinxProtobufGen.get()}:jvm8@jar"
        }
    }

    generateProtoTasks { all().forEach { it.plugins { id("kotlinx-protobuf-gen") {} } } }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        )
    }
}

dependencies {

    // core
    implementation(libs.androidx.core.ktx)

    // worker
    implementation(libs.androidx.work)
    implementation(libs.androidx.work.ktx)

    implementation(libs.androidx.datastore.preferences)

    // hilt di
    implementation(libs.hilt.android)
    implementation(libs.hilt.common)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    // compose: @Immutable on two data models, and Color/toArgb in GenerateQrBitmap. No
    // composables here, so the Compose compiler plugin is deliberately not applied.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.ui.graphics)

    // room
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    // ktor
    api(libs.ktor.client.core)
    api(libs.ktor.client.negotiation)
    api(libs.ktor.client.serialization.kotlinx)

    // serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    // crypto
    api(libs.wallet.core)

    // encryption
    api(libs.bcprov.jdk18on)

    // other
    // compileOnly because AGP rejects a direct local .aar dependency in a module that
    // builds its own AAR. :app provides it at runtime; see #5793 for the real fix.
    compileOnly(files("../app/libs/mobile-tss-lib.aar"))
    api(libs.timber)
    implementation(libs.spark.core)
    implementation(libs.apache.compress)
    implementation(libs.apache.compress.xz)
    implementation(libs.core.zxing)
    implementation(libs.androidx.security)

    // test
    // Not compileOnly: TssMessenger implements the gomobile `tss.Messenger` interface, so JUnit
    // cannot even resolve a test class that names it unless the AAR's classes are on the test
    // runtime classpath too. Only the interface is loaded — instantiating a native tss type still
    // needs the gojni library and stays out of unit tests.
    testImplementation(files("../app/libs/mobile-tss-lib.aar"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.wallet.core)
    // The runner named by `testInstrumentationRunner` has to be on the androidTest classpath
    // itself; nothing else puts it there.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
