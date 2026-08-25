rootProject.name = "emberhold"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Dependency graph (matches docs/engineering README).
// NOTE: module dependency wiring (implementation project(...)) is added in each
// module's build.gradle.kts, but for T0 skeleton we only wire Core as base and
// let the rest be leaf placeholders. Cross-module deps get wired in their own
// tasks (T2+ per TASKS.md). Keeping this minimal now to avoid compile order issues.
include(
    "core",
    "temperature",
    "storm",
    "shelter",
    "expedition",
    "mobs",
    "events",
    "settlement",
    "economy",
    "dist"
)
