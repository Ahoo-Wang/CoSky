plugins {
    `java-library`
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
dependencies {
    implementation(platform(project(":cosky-dependencies")))
    api("org.springframework.cloud:spring-cloud-openfeign-core")
}
