dependencies {
    api(project(":spring-cloud-starter-cosky-discovery"))
    api("org.springframework.cloud:spring-cloud-starter-netflix-ribbon:2.2.8.RELEASE")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${rootProject.ext.get("springBootVersion")}")
}
