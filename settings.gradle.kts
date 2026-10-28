pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "testcontainers-sefaz-build"

include(
    "sefaz-mock-server",
    "testcontainers-sefaz",
    "testcontainers-sefaz-spring-boot",
    "examples:java-nfe-example",
)
