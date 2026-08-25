// net.emberhold.events — EmberMobs + EmberEvents (spec 06).
// Dependency chain: core ← temperature ← storm ← shelter ← expedition ← events.
// Reads storm states, expedition context (threat budget), temperature value types.
dependencies {
    implementation(project(":core"))
    implementation(project(":temperature"))
    implementation(project(":storm"))
    implementation(project(":shelter"))
    implementation(project(":expedition"))
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.cronutils:cron-utils:9.2.1")
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
