plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "cev"
include(":SKPokerEval")
project(":SKPokerEval").projectDir = settingsDir.resolve("ext/SKPokerEval/java/SKPokerEval")
include(":SKPokerEval:evaluator")
include(":SKPokerEval:generator")
/*
include(":evaluator")
project(":evaluator").projectDir = settingsDir.resolve("ext/SKPokerEval/java/SKPokerEval/evaluator")
include(":generator")
project(":generator").projectDir = settingsDir.resolve("ext/SKPokerEval/java/SKPokerEval/generator")*/