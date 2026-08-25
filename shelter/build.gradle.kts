// net.emberhold.shelter — EmberShelter (spec 04).
// Depends on core + storm + temperature (dependency graph: core ← temperature ← storm ← shelter).
// Shelter reads temperature's ExposureVerdict/ShelterVerdict value types and the storm sector
// state (wind for drafty penalty), so it sits above both.
dependencies {
    implementation(project(":core"))
    implementation(project(":temperature"))
    implementation(project(":storm"))
    // The scanner + persistence touch Block/Player; tests need paper-api on classpath.
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
