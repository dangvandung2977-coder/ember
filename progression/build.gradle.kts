// net.emberhold.progression — EmberProgression (spec 05 §II).
// Dependency chain: core ← temperature ← storm ← shelter ← expedition ← events ← progression.
// Progression sits downstream of Events (per the architecture diagram): it reads the event
// bus for first-time-behaviour Note awards, and owns Field Notes / Skill lines / Gear tiers.
dependencies {
    implementation(project(":core"))
    implementation(project(":events"))
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
