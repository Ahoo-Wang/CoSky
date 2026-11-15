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
    // Reactor Netty brings the Intel build only, and the load-balanced client's DNS resolver logs an error without this
    // one on a Mac with Apple silicon.
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
}
