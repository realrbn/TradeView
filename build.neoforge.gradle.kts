plugins {
    id("net.neoforged.moddev") version "2.0.147"
}

version = "${property("mod_version")}-${sc.current.version}-neoforge"
base.archivesName = "tradeview"

val javaVersion = (findProperty("java_version") ?: "25").toString().toInt()
// 1.21.1 and 1.21.11 share their loader-independent code with Fabric in src/common-<minecraft>.
val legacyCommon = rootProject.file("src/common-${sc.current.version}")
val legacy = legacyCommon.isDirectory

sourceSets.main {
    if (legacy) {
        java.srcDirs(legacyCommon.resolve("java"), rootProject.file("src/neoforge-${sc.current.version}/java"))
        resources.srcDirs(rootProject.file("src/common/resources"), legacyCommon.resolve("resources"),
            rootProject.file("src/neoforge-${sc.current.version}/resources"))
    } else {
        java.srcDirs(rootProject.file("src/common/java"), rootProject.file("src/neoforge/java"))
        resources.srcDirs(rootProject.file("src/common/resources"), rootProject.file("src/neoforge/resources"))
    }
}

// Optional local compatibility smoke test; never bundled into the distributable JAR.
if (hasProperty("tradeCyclingJar")) {
    dependencies.add("localRuntime", files(property("tradeCyclingJar")))
}

// Scripted client run (real world, villager and trade screen): gradlew :<version>-neoforge:runValidation
val validationSources = rootProject.file("src/validation-neoforge-${sc.current.version}")
val validation = if (validationSources.isDirectory) sourceSets.create("validation") {
    java.setSrcDirs(listOf(validationSources.resolve("java")))
    resources.setSrcDirs(listOf(validationSources.resolve("resources")))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
} else null

neoForge {
    version = property("neoforge_version") as String
    val tradeviewMod = mods.create("tradeview") { sourceSet(sourceSets.main.get()) }
    val validationMod = validation?.let { set -> mods.create("tradeview_validation") { sourceSet(set) } }
    runs {
        // The validation fixture only loads in its own run.
        register("client") { client(); loadedMods.set(listOf(tradeviewMod)) }
        register("server") { server(); loadedMods.set(listOf(tradeviewMod)) }
        if (validationMod != null) register("validation") {
            client()
            sourceSet = validation
            gameDirectory = project.file("build/run/validation")
            loadedMods.set(listOf(tradeviewMod, validationMod))
        }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to sc.current.version,
        "neoforge_version" to project.property("neoforge_version").toString(),
        "java_version" to javaVersion.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("META-INF/neoforge.mods.toml", "*.mixins.json")) { expand(props) }
    // The shared mixin config targets the 26.x classes; legacy versions bring their own.
    if (legacy) exclude("tradeview.mixins.json")
}

tasks.named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }
tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}
java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    toolchain.languageVersion = JavaLanguageVersion.of(maxOf(javaVersion, 21))
}
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_${base.archivesName.get()}" } }
}
