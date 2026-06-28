plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
    alias(libs.plugins.shadow)
    id("com.diffplug.spotless") version "8.7.0"
    pmd
}

group = "com.vomlabs.dialect"
version = "0.2.0-ALPHA"
description = "AI-powered language enforcement, detection, translation, and moderation layer for Minecraft server chat."

val serverDir: String = providers.gradleProperty("serverDir")
    .orElse(providers.systemProperty("user.home").map { "$it/Documents/mc-server/1.21.11" })
    .get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.faststats.dev/releases")
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
    implementation("dev.faststats.metrics:bukkit:0.27.1")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.paper.api)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

spotless {
    java {
        target("src/*/java/**/*.java")
        endWithNewline()
        trimTrailingWhitespace()
        indentWithSpaces(4)
        removeUnusedImports()
        toggleOffOn()
    }
}

pmd {
    ruleSetConfig = rootProject.resources.text.fromFile("config/pmd/ruleset.xml")
    rulesMinimumPriority = 5
    isConsoleOutput = true
    isIgnoreFailures = false
}

tasks.named("pmdTest") {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-XDstringConcat=inline")
}

tasks {
    shadowJar {
        archiveBaseName.set("LazyDialect")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        minimize {
            exclude(dependency("com.github.ben-manes.caffeine:.*"))
        }

        relocate("dev.faststats", "com.vomlabs.dialect.libs.faststats")

        mergeServiceFiles()
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.MF")
        exclude("META-INF/maven/**")
        exclude("META-INF/native-image/**")
        exclude("**/*.kotlin_module")
        exclude("**/package-info.class")

        exclude("com/fasterxml/jackson/core/internal/shaded/**")
        exclude("redis/clients/jedis/timeseries/**")
        exclude("redis/clients/jedis/search/**")
        exclude("redis/clients/jedis/graph/**")
        exclude("redis/clients/jedis/json/**")
        exclude("redis/clients/jedis/bloom/**")
        exclude("redis/clients/jedis/modules/**")
        exclude("redis/clients/jedis/providers/**")
        exclude("redis/clients/jedis/args/**")
        exclude("redis/clients/jedis/exceptions/**")
        exclude("net/kyori/adventure/bossbar/**")
        exclude("net/kyori/adventure/dialog/**")
        exclude("net/kyori/adventure/inventory/**")
        exclude("net/kyori/adventure/nbt/**")
        exclude("net/kyori/adventure/resource/**")
        exclude("net/kyori/adventure/pointer/**")
        exclude("net/kyori/adventure/permission/**")
        exclude("net/kyori/adventure/identity/**")
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
