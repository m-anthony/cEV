plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val projectVersion = "1.4.4"
allprojects {
    group = "com.snaky.poker.cev"
    version = projectVersion

    repositories {
        google()
        mavenCentral()
    }

    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(libs.versions.java.get().toInt())
        }
    }

}