plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id ("com.gradleup.shadow") version "9.3.0"
}

group = "com.coolrequest.tool"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2024.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
    implementation("org.json:json:20251224")
    implementation(files("D:\\project\\coolrequest-vip\\sub-projects\\coolrequest-tools\\build\\libs\\coolrequest-tool-1.0-SNAPSHOT.jar"))
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

