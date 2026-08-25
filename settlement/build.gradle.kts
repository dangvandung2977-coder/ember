// net.emberhold.settlement — EmberSettlement (spec 07 Part A).
// Dependency chain: core ← temperature ← storm ← shelter ← expedition ← events ← settlement.
// Reads events/storm (blizzard drain hook), shelter (machine specs), expedition (context).
dependencies {
    implementation(project(":core"))
    implementation(project(":temperature"))
    implementation(project(":storm"))
    implementation(project(":shelter"))
    implementation(project(":expedition"))
    implementation(project(":events"))
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
