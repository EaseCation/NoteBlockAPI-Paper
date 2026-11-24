plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    `maven-publish`
}

// EaseCation fork coordinates/versioning (upstream com.xxmicloxx 1.7.0-SNAPSHOT), see GeyserWorkspace docs/maven-coordinates.md
group = "net.easecation.fork.xxmicloxx"
version = "1.7.0-ec.1"
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
    implementation("org.bstats:bstats-bukkit:3.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("name" to "NoteBlockAPI", "mainClass" to "com.xxmicloxx.NoteBlockAPI.NoteBlockAPI", "version" to project.version)
    }
}
tasks.jar {
    archiveClassifier.set("plain")
    destinationDirectory.set(layout.buildDirectory.dir("api"))
}
tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.bstats", "com.xxmicloxx.NoteBlockAPI.bstats")
}
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.test { useJUnitPlatform() }

// Published to the EaseCation private repository (Aliyun Yunxiao Packages). artifactId = project name.
// Credentials: yunxiaoMavenUsername / yunxiaoMavenPassword in ~/.gradle/gradle.properties.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                description.set("EaseCation fork of NoteBlockAPI")
                properties.set(mapOf("upstream.groupId" to "com.xxmicloxx", "upstream.version" to "1.7.0-SNAPSHOT"))
            }
        }
    }
    repositories {
        maven {
            name = "yunxiaoMaven"
            url = uri(
                if (version.toString().endsWith("-SNAPSHOT"))
                    "https://easecation-cn-hangzhou.devops.aliyuncs.com/packages/api/protocol/maven/14075-snapshot-bjmmxv"
                else
                    "https://easecation-cn-hangzhou.devops.aliyuncs.com/packages/api/protocol/maven/14075-release-h64s7e"
            )
            credentials(PasswordCredentials::class)
        }
    }
}
