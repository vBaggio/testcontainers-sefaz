// Publicação no Maven Central pelo Central Portal (ADR-007).
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish")
}

configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = false)
    // Sem chave GPG (build local, CI de PR) a publicação local continua funcionando sem assinatura.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set(project.description)
        url.set("https://github.com/vBaggio/testcontainers-sefaz")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("vBaggio")
                name.set("vBaggio")
                url.set("https://github.com/vBaggio")
            }
        }
        scm {
            url.set("https://github.com/vBaggio/testcontainers-sefaz")
            connection.set("scm:git:git://github.com/vBaggio/testcontainers-sefaz.git")
            developerConnection.set("scm:git:ssh://git@github.com/vBaggio/testcontainers-sefaz.git")
        }
    }
}
