plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.jar.configure {
    exclude("application.yaml", "bootstrap.yaml")
}

application {
    mainClass.set("me.ahoo.cosky.mirror.CoskyMirrorServer")

    applicationDefaultJvmArgs = listOf(
        "-Xms512M",
        "-Xmx512M",
        "-XX:MaxMetaspaceSize=128M",
        "-XX:MaxDirectMemorySize=256M",
        "-Xss1m",
        "-server",
        "-XX:+UseG1GC",
        "-Xlog:gc*:file=logs/${applicationName}-gc.log:time,tags:filecount=10,filesize=32M",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=data",
        "-Dcom.sun.management.jmxremote",
        "-Dcom.sun.management.jmxremote.authenticate=false",
        "-Dcom.sun.management.jmxremote.ssl=false",
        "-Dcom.sun.management.jmxremote.port=5555",
        "-Dspring.cloud.bootstrap.enabled=true",
        "-Dspring.cloud.bootstrap.location=config/bootstrap.yaml",
        "-Dspring.config.location=file:./config/"
    )
}


dependencies {
    implementation(platform(project(":cosky-dependencies")))
    implementation(project(":spring-cloud-starter-cosky-config"))
    implementation(project(":spring-cloud-starter-cosky-discovery"))
    implementation("com.google.guava:guava")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:2021.1")
    compileOnly("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
