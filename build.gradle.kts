plugins {
    // Root aggregator only — no plugins applied here.
}

allprojects {
    group = "net.emberhold"
    version = providers.gradleProperty("ember.artifact.version").get()
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        // PlaceholderAPI API (soft dep, optional integration for the PlaceholderExpansion).
        maven("https://repo.extendedclip.com/releases/")
    }

    // Toolchain via the extension (bare `java {}` is ambiguous inside subprojects).
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    // Paper API is provided by the server at runtime — not bundled into modules.
    // Modules share it as compileOnly so they cross-compile cleanly.
    val paperApiVersion = providers.gradleProperty("ember.paper.api.version").get()
    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:$paperApiVersion")

        "testImplementation"(platform("org.junit:junit-bom:${providers.gradleProperty("ember.junit.version").get()}"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.compilerArgs.add("-Werror")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Jar>().configureEach {
        // Deterministic builds.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
