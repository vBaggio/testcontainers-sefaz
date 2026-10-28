plugins {
    id("sefaz.java-conventions")
    `java-library`
    id("sefaz.publishing-conventions")
}

description = "Integração do testcontainers-sefaz com Spring Boot: @ServiceConnection, urlProperty e @SefazScenario"

// A CI roda os testes contra a última 3.x e a 4.x atual: -PspringBootVersion=4.1.2
val springBootVersion = providers.gradleProperty("springBootVersion").getOrElse(libs.versions.spring.boot.get())

dependencies {
    api(project(":testcontainers-sefaz"))

    // Spring vem da aplicação do usuário; o módulo não fixa versão nem puxa Spring sozinho.
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly(libs.spring.boot.testcontainers)
    compileOnly("org.springframework:spring-test")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    dependsOn(":sefaz-mock-server:jibDockerBuild")
    systemProperty("sefaz.image", "ghcr.io/vbaggio/sefaz-mock-server:${project.version}")
    inputs.property("springBootVersion", springBootVersion)
}
