// net.emberhold.dist — the single deployable plugin jar.
// Aggregates all modules into one fat-jar with a hand-rolled task (no third-party
// shadow plugin, per CONVENTIONS: avoid new deps).
//
// IMPORTANT: many bundled libraries (Flyway, Jackson, etc.) register SPI
// implementations via META-INF/services/*. With duplicatesStrategy=EXCLUDE those
// service files get clobbered (first-wins) and SPI lookups silently lose providers
// (e.g. Flyway's CoreResourceTypeProvider registers the SQL migration prefixes).
// We therefore MERGE every META-INF/services/* file across the whole runtime
// classpath (concatenating + deduping provider lines) and drop the per-jar copies.

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core"))
    implementation(project(":temperature"))
    implementation(project(":storm"))
    implementation(project(":shelter"))
    implementation(project(":expedition"))
    implementation(project(":mobs"))
    implementation(project(":events"))
    implementation(project(":progression"))
    implementation(project(":settlement"))
    implementation(project(":economy"))
    // Shared "ember" PlaceholderExpansion lives in core but extends a PlaceholderAPI type;
    // compileOnly (not bundled) so dist can see the supertype without shipping PAPI.
    compileOnly("me.clip:placeholderapi:2.12.3")
}

// Helper: merge all META-INF/services/* across a list of jar files, returning
// providerInterface -> ordered unique implementation class names.
// NOTE: implement with Gradle's FileTree + Kotlin collections only — the Kotlin DSL
// script compiler in this toolchain does not resolve java.util.* / java.util.zip.*.
fun mergeServicesFrom(jars: List<File>, project: Project): Map<String, LinkedHashSet<String>> {
    val merged = mutableMapOf<String, LinkedHashSet<String>>()
    for (f in jars) {
        if (!f.isFile) continue
        val tree = project.zipTree(f).matching { include("META-INF/services/**") }
        for (svcFile in tree) {
            val pathStr = svcFile.path.replace('\\', '/')
            if (!pathStr.contains("/META-INF/services/")) continue
            val provider = pathStr.substringAfter("/META-INF/services/")
            val text = svcFile.readText()
            val lines = text.lines()
                .map { line -> line.substringBefore('#').trim() }
                .filter { line -> line.isNotEmpty() }
            merged.getOrPut(provider) { linkedSetOf() }.addAll(lines)
        }
    }
    return merged
}

// -----------------------------------------------------------------------------
// Merge all META-INF/services/* across the runtime classpath into $buildDir/mergedServices.
// Service file names are the provider interface; the content is the set of
// implementation class names (one per line, # = comment).
// -----------------------------------------------------------------------------
val mergeServiceFiles = tasks.register("mergeServiceFiles") {
    val outDir = layout.buildDirectory.dir("mergedServices")
    inputs.files(configurations.runtimeClasspath)
    outputs.dir(outDir)

    doLast {
        val files: List<java.io.File> = configurations.runtimeClasspath.get().toList()
        val merged = mergeServicesFrom(files, project)
        val root = outDir.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        for (entry in merged) {
            val provider = entry.key
            val dest = root.resolve(provider).apply { parentFile.mkdirs() }
            dest.writeText(entry.value.joinToString("\n") + "\n")
        }
    }
}

val fatJar = tasks.register<Jar>("buildFatJar") {
    dependsOn(mergeServiceFiles)
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Implementation-Title" to "EmberHold")
        attributes("Implementation-Version" to project.version.toString())
    }

    // Project classes + packaged resources.
    from(sourceSets["main"].output)

    // Runtime dependency jars (externally resolved), expanded as zip trees.
    // Exclude their raw per-jar service files; we emit merged copies below.
    val runtimeFiles: List<java.io.File> = configurations.runtimeClasspath.get().toList()
    from(runtimeFiles.map { f -> if (f.isDirectory) f else zipTree(f) }) {
        exclude("META-INF/services/**")
    }

    // Merged SPI providers (overrides any stray copy).
    from(layout.buildDirectory.dir("mergedServices")) {
        into("META-INF/services")
    }

    // Explicit task-order dependency on each module jar (Gradle implicit-dep validation).
    dependsOn(rootProject.project(":core").tasks.named("jar"))
    dependsOn(rootProject.project(":temperature").tasks.named("jar"))
    dependsOn(rootProject.project(":storm").tasks.named("jar"))
    dependsOn(rootProject.project(":shelter").tasks.named("jar"))
    dependsOn(rootProject.project(":expedition").tasks.named("jar"))
    dependsOn(rootProject.project(":mobs").tasks.named("jar"))
    dependsOn(rootProject.project(":events").tasks.named("jar"))
    dependsOn(rootProject.project(":progression").tasks.named("jar"))
    dependsOn(rootProject.project(":settlement").tasks.named("jar"))
    dependsOn(rootProject.project(":economy").tasks.named("jar"))
}

// Ensure a plain `build` also produces the deployable jar.
tasks.named("assemble") { dependsOn(fatJar) }
tasks.named("build") { dependsOn(fatJar) }
