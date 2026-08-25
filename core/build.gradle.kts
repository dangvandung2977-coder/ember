// net.emberhold.core — EmberCore (spec 01).
// T1: EmberApi + service registry + boot lifecycle.
// Spec §7 explicitly names HikariCP + Flyway; §5 names SnakeYAML — these are the
// only added dependencies and they are called out in the spec (not "extra").

val hikariVersion = "5.1.0"
val flywayVersion = "10.20.1"
val postgresVersion = "42.7.4"
val snakeYamlVersion = "2.2"
val placeholderApiVersion = "2.12.3"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.yaml:snakeyaml:$snakeYamlVersion")

    // Optional soft-dep: the shared "ember" PlaceholderExpansion (only compiled against, never bundled).
    compileOnly("me.clip:placeholderapi:$placeholderApiVersion")

    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("ember.paper.api.version").get()}")
}
