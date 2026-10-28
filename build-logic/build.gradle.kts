plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.maven.publish)
    implementation(libs.jib.gradle)
}
