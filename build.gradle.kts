import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
    // Turns legacy non-modular jars (JLaTeXMath) into proper JPMS modules, matching JabRef's own build
    id("org.gradlex.extra-java-module-info") version "1.14.2"
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
    api("org.jspecify:jspecify:1.0.0")
    implementation("org.jsoup:jsoup:1.22.2")
    // TeX math rendering; GPL 2.0 with the linking ("Classpath") exception, like OpenJFX
    implementation("org.scilab.forge:jlatexmath:1.0.7")

    compileOnly("org.openjfx:javafx-base:$javafxVersion:$jfxPlatform")
    compileOnly("org.openjfx:javafx-graphics:$javafxVersion:$jfxPlatform")
    // Only for the optional RichTextArea renderer (module-info: requires static)
    compileOnly("org.openjfx:javafx-controls:$javafxVersion:$jfxPlatform")
    compileOnly("org.openjfx:jfx-incubator-input:$javafxVersion:$jfxPlatform")
    compileOnly("org.openjfx:jfx-incubator-richtext:$javafxVersion:$jfxPlatform")

    testImplementation("org.openjfx:javafx-base:$javafxVersion:$jfxPlatform")
    testImplementation("org.openjfx:javafx-graphics:$javafxVersion:$jfxPlatform")
    testImplementation("org.openjfx:javafx-controls:$javafxVersion:$jfxPlatform")
    testImplementation("org.openjfx:jfx-incubator-input:$javafxVersion:$jfxPlatform")
    testImplementation("org.openjfx:jfx-incubator-richtext:$javafxVersion:$jfxPlatform")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// JLaTeXMath 1.0.7 ships no module descriptor, and its optional Greek/Cyrillic fonts sit in
// companion resource-only jars, so Gradle leaves it on the classpath and `requires jlatexmath`
// fails to resolve. Promote it to a single proper module (fonts merged in so their .ttf resources
// resolve within the module) named `jlatexmath`, exporting its packages and reading the JDK
// modules jdeps reports it uses (java.desktop for Java2D, java.xml for its font-metric config).
extraJavaModuleInfo {
    failOnMissingModuleInfo = false
    module("org.scilab.forge:jlatexmath", "jlatexmath") {
        requires("java.desktop")
        requires("java.xml")
        exportAllPackages()
        mergeJar("org.scilab.forge:jlatexmath-font-greek")
        mergeJar("org.scilab.forge:jlatexmath-font-cyrillic")
    }
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
    useJUnitPlatform {
        excludeTags("gui")
    }
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

// Tests of the RichTextArea renderer need the JavaFX toolkit: a display or xvfb-run
val guiTest by tasks.registering(Test::class) {
    description = "Runs tests requiring the JavaFX toolkit (tag 'gui'); needs a display or xvfb-run"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("gui")
    }
    modularity.inferModulePath = false
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
