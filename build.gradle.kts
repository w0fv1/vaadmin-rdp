plugins {
    `java-library`
}

group = "dev.w0fv1"
repositories { mavenCentral() }

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
tasks.test { useJUnitPlatform() }
