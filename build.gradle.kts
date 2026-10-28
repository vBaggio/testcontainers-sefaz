allprojects {
    group = "io.github.vbaggio"
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
}
