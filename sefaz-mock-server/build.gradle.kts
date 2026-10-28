plugins {
    id("sefaz.java-conventions")
    application
    id("sefaz.docker-image")
}

description = "SEFAZ fake (NF-e/NFC-e, leiaute 4.00) empacotada como imagem Docker"

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.bouncycastle.bcpkix)
}

application {
    mainClass = "io.github.vbaggio.sefaz.mock.SefazMockServer"
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/sefaz-mock-server.properties") {
        expand("version" to version)
    }
}

val imageRepository = providers.gradleProperty("imageRepository").getOrElse("ghcr.io/vbaggio/sefaz-mock-server")
val imagePlatforms = providers.gradleProperty("jibPlatforms").orNull

jib {
    from {
        image = providers.gradleProperty("jibBaseImage").getOrElse("eclipse-temurin:21-jre-alpine")
        if (imagePlatforms != null) {
            platforms {
                imagePlatforms.split(",").map { it.trim() }.forEach { spec ->
                    platform {
                        os = spec.substringBefore("/")
                        architecture = spec.substringAfter("/")
                    }
                }
            }
        }
    }
    to {
        image = "$imageRepository:${project.version}"
    }
    container {
        mainClass = "io.github.vbaggio.sefaz.mock.SefazMockServer"
        ports = listOf("8443", "8080", "8081")
        jvmFlags = listOf("-XX:+UseSerialGC", "-XX:MaxRAMPercentage=75", "-Xss512k")
        labels = mapOf(
            "org.opencontainers.image.source" to "https://github.com/vBaggio/testcontainers-sefaz",
            "org.opencontainers.image.licenses" to "Apache-2.0",
            "org.opencontainers.image.version" to project.version.toString(),
        )
    }
}

tasks.test {
    if (providers.gradleProperty("updateFixtures").isPresent) {
        systemProperty("sefaz.updateFixtures", "true")
    }
}
