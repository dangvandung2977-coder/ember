// net.emberhold.storm — EmberStorm (spec 03).
// Wires core dependency at T16 (EmberApi/EventBus/schedulers for the director loop).
// temperature dependency is added when the sector-weather → WarmthInput bridge lands (T18+).
dependencies {
    implementation(project(":core"))
    // sector-weather → WarmthInput bridge (spec 03 §1): Storm implements temperature's
    // StormWeatherProvider and registers it via the EmberApi service registry.
    implementation(project(":temperature"))
    // The director/persistence touch Bukkit Player/Plugin; tests need paper-api on classpath.
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
