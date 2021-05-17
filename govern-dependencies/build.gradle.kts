dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.ext.get("springBootVersion")}"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${rootProject.ext.get("springCloudVersion")}"))
    constraints {
        api("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
        api("com.google.guava:guava:${rootProject.ext.get("guavaVersion")}")
        api("io.springfox:springfox-boot-starter:${rootProject.ext.get("springfoxVersion")}")
    }
}
