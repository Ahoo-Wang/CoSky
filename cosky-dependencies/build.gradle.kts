dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.ext.get("springBootVersion")}"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${rootProject.ext.get("springCloudVersion")}"))
//    api(platform("me.ahoo.cosid:cosid-bom:${rootProject.ext.get("cosIdVersion")}"))
    constraints {
        api("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
        api("com.google.guava:guava:${rootProject.ext.get("guavaVersion")}")
        api("commons-io:commons-io:${rootProject.ext.get("commonsIOVersion")}")
        api("io.springfox:springfox-boot-starter:${rootProject.ext.get("springfoxVersion")}")
        api("io.dropwizard.metrics:metrics-core:${rootProject.ext.get("metricsVersion")}")
    }
}
