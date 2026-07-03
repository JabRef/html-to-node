import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "org.jabref"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // JavaFX 26 ships Java-24 class files; 24 keeps the library usable one release below JabRef's 25
    options.release = 24
    options.encoding = "UTF-8"
}

// JavaFX artifacts are platform-specific; consumers provide their own JavaFX (hence compileOnly)
val javafxVersion = "26.0.1"
val jfxPlatform = run {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        osName.contains("win") -> "win"
        osName.contains("mac") || osName.contains("darwin") -> if (arch.contains("aarch64")) "mac-aarch64" else "mac"
        else -> if (arch.contains("aarch64")) "linux-aarch64" else "linux"
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.2")

    compileOnly("org.openjfx:javafx-base:$javafxVersion:$jfxPlatform")
    compileOnly("org.openjfx:javafx-graphics:$javafxVersion:$jfxPlatform")

    testImplementation("org.openjfx:javafx-base:$javafxVersion:$jfxPlatform")
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion:$jfxPlatform")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.javadoc {
    // Document the exported API only; org.jabref.htmltonode.internal stays out of the docs.
    // The compiled classes are patched in so references to internal types still resolve.
    exclude("org/jabref/htmltonode/internal/**")
    val classesDirs = sourceSets.main.get().output.classesDirs
    options {
        this as StandardJavadocDocletOptions
        encoding = "UTF-8"
        addStringOption("-patch-module", "org.jabref.htmltonode=${classesDirs.asPath}")
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}

// Same publishing setup as JabRef's jablib: snapshots land on
// https://central.sonatype.com/repository/maven-snapshots/, which JabRef's build already resolves
mavenPublishing {
    configure(JavaLibrary(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources(),
    ))

    publishToMavenCentral()
    signAllPublications()

    coordinates("org.jabref", "html-to-node", version.toString())

    pom {
        name = "html-to-node"
        description = "Renders HTML as plain JavaFX nodes (TextFlow/Text/ImageView) without javafx.web"
        inceptionYear = "2026"
        url = "https://github.com/JabRef/html-to-node/"
        licenses {
            license {
                name = "MIT"
                url = "https://github.com/JabRef/html-to-node/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "jabref"
                name = "JabRef Developers"
                url = "https://github.com/JabRef/"
            }
        }
        scm {
            url = "https://github.com/JabRef/html-to-node"
            connection = "scm:git:https://github.com/JabRef/html-to-node"
            developerConnection = "scm:git:git@github.com:JabRef/html-to-node.git"
        }
    }
}

// Tests run on the classpath: no module patching needed, and plain Text/TextFlow works headless
tasks.compileTestJava {
    modularity.inferModulePath = false
}

tasks.test {
    useJUnitPlatform()
    modularity.inferModulePath = false
    systemProperty("java.awt.headless", "true")
    // JavaFX loads its native font/graphics libraries from the classpath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
