import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("com.diffplug.spotless") version "7.0.2"
}

group = providers.gradleProperty("pluginGroup").getOrElse("local.mcpstop")
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion").get())
        bundledPlugin("com.intellij.mcpServer")
        bundledPlugin("org.jetbrains.idea.maven")
        bundledPlugin("intellij.testRunner.plugin")

        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").getOrElse("21").toInt())
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "262"
        }
    }
    publishing {
        token =
            providers.environmentVariable("PUBLISH_TOKEN").orElse(providers.gradleProperty("intellijPlatformPublishingToken").orElse(""))
    }
    pluginVerification {
        // Marketplace naming rules; the plugin is installed from disk, never published.
        freeArgs = listOf("-mute", "TemplateWordInPluginId,TemplateWordInPluginName")
        failureLevel = VerifyPluginTask.FailureLevel.ALL
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
