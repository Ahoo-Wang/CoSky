plugins {
    alias(libs.plugins.kotlin.spring)
    application
}

application {
    mainClass = "me.ahoo.cosky.examples.provider.ProviderApplicationKt"
}

dependencies {
    implementation(project(":spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // Refreshing when a Config changes.
    implementation("org.springframework.cloud:spring-cloud-context")
}
