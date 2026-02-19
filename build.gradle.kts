plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val projectVersion = "1.0-SNAPSHOT"
allprojects {
    group = "com.snaky.poker.cev"
    version = projectVersion

    repositories {
        google()
        mavenCentral()
    }
}