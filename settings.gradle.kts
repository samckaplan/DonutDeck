pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.johnrengelman.shadow") version "8.1.1" apply false
}

rootProject.name = "DonutDeck"