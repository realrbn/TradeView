plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod_version")}-${sc.current.version}-fabric"
base.archivesName = "tradeview"

sourceSets.main {
    java.srcDirs(rootProject.file("src/common/java"), rootProject.file("src/fabric/java"))
    resources.srcDirs(rootProject.file("src/common/resources"), rootProject.file("src/fabric/resources"))
}

repositories {
    maven("https://maven.terraformersmc.com/")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    if (hasProperty("tradeCyclingJar")) modRuntimeOnly(files(property("tradeCyclingJar")))
    if (hasProperty("testHeroOfVillagePlus")) {
        modRuntimeOnly(files(rootProject.file("build/compat/HotVP-fabric-${sc.current.version}.jar")))
        modRuntimeOnly(files(rootProject.file("build/compat/cloth-config-${sc.current.version}.jar")))
    }
    if (hasProperty("testModMenu") || hasProperty("withModMenu"))
        modRuntimeOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "tradeview-test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to sc.current.version,
        "loader_version" to project.property("fabric_loader_version").toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 25 }
java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_${base.archivesName.get()}" } }
}
