plugins {
    id("sefaz.java-conventions")
}

description = "Exemplo: emissão de ponta a ponta com a Java_NFe contra a SEFAZ fake (não publicado)"

dependencies {
    implementation(libs.java.nfe)

    testImplementation(project(":testcontainers-sefaz"))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.bouncycastle.bcpkix)
}

tasks.test {
    dependsOn(":sefaz-mock-server:jibDockerBuild")
    systemProperty("sefaz.image", "ghcr.io/vbaggio/sefaz-mock-server:${project.version}")
}
