@file:Suppress("DEPRECATION")

// Obfuscated Minecraft versions (1.20.1, 1.21.1, 1.21.11). Each has its own source tree in
// src/fabric-<minecraft>, because their APIs differ too much from the shared 26.x sources.
plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod_version")}-${sc.current.version}-fabric"
base.archivesName = "tradeview"

val javaVersion = property("java_version").toString().toInt()

// Versions that also have a NeoForge build share their loader-independent code in src/common-<minecraft>.
val legacyCommon = rootProject.file("src/common-${sc.current.version}")
sourceSets.main {
    java.srcDirs(rootProject.file("src/fabric-${sc.current.version}/java"))
    resources.srcDirs(rootProject.file("src/common/resources"), rootProject.file("src/fabric-${sc.current.version}/resources"))
    if (legacyCommon.isDirectory) {
        java.srcDir(legacyCommon.resolve("java"))
        resources.srcDir(legacyCommon.resolve("resources"))
    }
}

repositories {
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modCompileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")

    if (hasProperty("tradeCyclingJar")) modRuntimeOnly(files(property("tradeCyclingJar")))
    if (hasProperty("testModMenu") || hasProperty("withModMenu"))
        modRuntimeOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
}

// Server gametests use each version's Fabric API; client gametests exist from 1.21.4 on.
val gameTestSources = rootProject.file("src/gametest-${sc.current.version}")
if (gameTestSources.isDirectory) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "tradeview-test"
            enableGameTests = true
            enableClientGameTests = stonecutter.eval(sc.current.version, ">=1.21.4")
            eula = true
        }
    }
    sourceSets.named("gametest") {
        java.setSrcDirs(listOf(gameTestSources.resolve("java")))
        resources.setSrcDirs(listOf(gameTestSources.resolve("resources")))
    }
}

// Versions without client gametests get a scripted client run instead: gradlew :<version>-fabric:runValidation
val validationSources = rootProject.file("src/validation-${sc.current.version}")
if (validationSources.isDirectory) {
    val validation = sourceSets.create("validation") {
        java.setSrcDirs(listOf(validationSources.resolve("java")))
        resources.setSrcDirs(listOf(validationSources.resolve("resources")))
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    }
    loom {
        mods {
            register("tradeview") { sourceSet(sourceSets.main.get()) }
            register("tradeview_validation") { sourceSet(validation) }
        }
        runs {
            register("validation") {
                client()
                source(validation)
                runDir("build/run/validation")
            }
        }
    }
    // Production check: the remapped release jar and the remapped fixture in a real intermediary client.
    val validationJar = tasks.register<Jar>("validationJar") {
        from(validation.output)
        archiveClassifier = "validation-dev"
        destinationDirectory = layout.buildDirectory.dir("devlibs")
    }
    val remapValidationJar = tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapValidationJar") {
        inputFile = validationJar.flatMap { it.archiveFile }
        classpath.from(sourceSets.main.get().output)
        archiveClassifier = "validation"
        destinationDirectory = layout.buildDirectory.dir("validation-libs")
    }
    val prodMods = configurations.create("prodValidationMods") { isTransitive = false }
    dependencies { prodMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}") }
    tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("prodValidation") {
        mods.from(tasks.named("remapJar"), remapValidationJar, prodMods)
        runDir = layout.buildDirectory.dir("run/prodValidation")
        useXVFB = false
    }
}

// Production check for versions with client gametests: the same tests against the remapped release jar.
if (gameTestSources.isDirectory && stonecutter.eval(sc.current.version, ">=1.21.4")) {
    val gametestJar = tasks.register<Jar>("gametestJar") {
        from(sourceSets.getByName("gametest").output)
        archiveClassifier = "gametest-dev"
        destinationDirectory = layout.buildDirectory.dir("devlibs")
    }
    val remapGametestJar = tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapGametestJar") {
        inputFile = gametestJar.flatMap { it.archiveFile }
        classpath.from(sourceSets.main.get().output)
        archiveClassifier = "gametest"
        destinationDirectory = layout.buildDirectory.dir("gametest-libs")
    }
    val prodMods = configurations.create("prodGameTestMods") { isTransitive = false }
    dependencies {
        prodMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
        prodMods(fabricApi.module("fabric-client-gametest-api-v1", property("fabric_api_version").toString()))
        prodMods(fabricApi.module("fabric-gametest-api-v1", property("fabric_api_version").toString()))
        if (hasProperty("testModMenu")) prodMods("maven.modrinth:modmenu:${property("modmenu_version")}")
    }
    tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("prodClientGameTest") {
        mods.from(tasks.named("remapJar"), remapGametestJar, prodMods)
        runDir = layout.buildDirectory.dir("run/prodClientGameTest")
        jvmArgs.addAll("-Dfabric.client.gametest", "-Dfabric-api.gametest.eula=true",
            "-Dfabric.client.gametest.testModResourcesPath=" + gameTestSources.resolve("resources").absolutePath)
        useXVFB = false
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to sc.current.version,
        "loader_version" to (project.findProperty("fabric_loader_min") ?: project.property("fabric_loader_version")).toString(),
        "fabric_api_version" to (project.findProperty("fabric_api_min") ?: project.property("fabric_api_version")).toString(),
        "java_version" to javaVersion.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    // The shared mixin configs target the 26.x classes; this tree brings its own.
    exclude("tradeview.mixins.json", "tradeview-fabric.mixins.json")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}
java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_${base.archivesName.get()}" } }
}
