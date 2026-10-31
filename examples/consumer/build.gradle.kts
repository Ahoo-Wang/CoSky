plugins {
    alias(libs.plugins.kotlin.spring)
    application
}

application {
    mainClass = "me.ahoo.cosky.examples.consumer.ConsumerApplicationKt"
}

dependencies {
    implementation(project(":spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
