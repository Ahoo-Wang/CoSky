plugins {
    application
}

application {
    applicationDefaultJvmArgs = listOf("-Dspring.cloud.bootstrap.enabled=true")
    mainClass.set("me.ahoo.govern.rest.RestApiServer")
}

dependencies {
    implementation(platform(project(":dependencies")))
    implementation("io.springfox:springfox-boot-starter")
    implementation(project(":spring-cloud-starter-config"))
    implementation(project(":spring-cloud-starter-discovery"))
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
