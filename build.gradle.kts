// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        dependencies {
            classpath( "com.yanzhenjie.andserver:plugin:2.1.12")
        }
    }

}
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.daggerHiltAndroid) apply false
}