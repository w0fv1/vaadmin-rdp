plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vaadin")
}

group = "dev.w0fv1"
version = "0.1.0"

vaadin {
    optimizeBundle = false
}

springBoot {
    mainClass.set("dev.w0fv1.vaadmin.rdp.sample.RdpSampleApplication")
}

dependencies {
    implementation(project(":"))
    developmentOnly("com.vaadin:vaadin-dev")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
