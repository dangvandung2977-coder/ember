// net.emberhold.economy — EmberEconomy (spec 07 Part B).
// Dependency chain: ... ← settlement ← economy (leaf). Reads settlement treasury for flows;
// core provides Db/EmberApi.
dependencies {
    implementation(project(":core"))
    implementation(project(":settlement"))
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
