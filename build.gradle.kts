plugins {
    `java-library`
}

group = "dev.w0fv1"

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
dependencies {
    api(platform("com.vaadin:vaadin-bom:" + providers.gradleProperty("vaadinVersion").get()))
    api(platform("org.springframework.boot:spring-boot-dependencies:" + providers.gradleProperty("springBootVersion").get()))
    api("com.vaadin:vaadin-spring-boot-starter")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
tasks.test { useJUnitPlatform() }
