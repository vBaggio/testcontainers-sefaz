plugins {
    id("sefaz.java-conventions")
    `java-library`
    id("sefaz.publishing-conventions")
}

description = "Módulo Testcontainers da SEFAZ fake: SefazContainer, cenários e API admin"

dependencies {
    api(libs.testcontainers)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/testcontainers-sefaz.properties") {
        expand("version" to version)
    }
}

// Os testes de integração rodam contra a imagem construída neste build, nunca contra uma tag publicada.
tasks.test {
    dependsOn(":sefaz-mock-server:jibDockerBuild")
    systemProperty("sefaz.image", "ghcr.io/vbaggio/sefaz-mock-server:${project.version}")
}
