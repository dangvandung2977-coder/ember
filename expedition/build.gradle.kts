// net.emberhold.expedition — EmberExpedition (spec 05).
// Dependency graph: core ← temperature ← storm ← shelter ← expedition.
// Reads shelter/temperature value types and storm state; depends on all four.
dependencies {
    implementation(project(":core"))
    implementation(project(":temperature"))
    implementation(project(":storm"))
    implementation(project(":shelter"))
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
