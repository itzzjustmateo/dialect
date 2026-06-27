plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
    alias(libs.plugins.shadow)
}

group = "com.vomlabs.dialect"
version = "1.0.0"
description = "AI-powered language enforcement, detection, translation, and moderation layer for Minecraft server chat."

val serverDir: String = providers.gradleProperty("serverDir")
    .orElse(providers.systemProperty("user.home").map { "$it/Documents/mc-server/1.21.11" })
    .get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    compileOnly(libs.paper.api)

    implementation(libs.adventure.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.caffeine)
    implementation(libs.jedis)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.paper.api)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-XDstringConcat=inline")
}

tasks {
    shadowJar {
        archiveBaseName.set("Dialect")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        mergeServiceFiles()
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("module-info.class")
    }

    build {
        dependsOn("clean")
        dependsOn(shadowJar)
    }

    named("classes") {
        mustRunAfter("clean")
    }

    val deployPlugin = register<Copy>("deployPlugin") {
        dependsOn(shadowJar)
        group = "deployment"
        description = "Copies the shadowJar to the target server's plugins directory."

        from(shadowJar.flatMap { it.archiveFile })
        into(providers.provider { file("$serverDir/plugins") })
    }

    register<DefaultTask>("setupServer") {
        group = "deployment"
        description = "Creates the target server directory structure with default config."

        doLast {
            val sDir = layout.projectDirectory.dir(serverDir)
            sDir.dir("plugins").asFile.mkdirs()
            sDir.dir("cache").asFile.mkdirs()
            sDir.dir("logs").asFile.mkdirs()
            sDir.dir("world").asFile.mkdirs()
            logger.lifecycle("Server directory structure ready at: $serverDir")

            val eula = sDir.file("eula.txt").asFile
            if (!eula.exists()) {
                eula.writeText("eula=true\n")
                logger.lifecycle("Created eula.txt (eula=true)")
            }
        }
    }

    register("deployAndRun") {
        group = "deployment"
        description = "Builds, deploys the plugin, then starts the Paper server."

        dependsOn(deployPlugin)
        finalizedBy("runServer")
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "description" to project.description
        )
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
        maxParallelForks = 1
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
}
