plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation(project(":cosky-service-provider-api"))

    implementation(platform(project(":cosky-dependencies")))
    implementation("io.springfox:springfox-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":spring-cloud-starter-cosky-config"))
    implementation(project(":spring-cloud-starter-cosky-discovery"))
    implementation("com.google.guava:guava")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    compileOnly("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
