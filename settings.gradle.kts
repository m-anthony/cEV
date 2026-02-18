plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "cev"
include(":cev-core")
include(":cev-ui")

include(":SKPokerEval")
project(":SKPokerEval").projectDir = settingsDir.resolve("ext/SKPokerEval/java/SKPokerEval")
include(":SKPokerEval:evaluator")
include(":SKPokerEval:generator")
