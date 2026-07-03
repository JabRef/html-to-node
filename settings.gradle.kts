plugins {
    // Same toolchain auto-provisioning as JabRef; JDK 25 is usually already in ~/.gradle/jdks
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "html-to-node"
