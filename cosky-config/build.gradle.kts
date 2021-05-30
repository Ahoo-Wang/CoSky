plugins {
    id("me.champeau.jmh") version "0.6.4"
}

dependencies {
    api(project(":cosky-core"))
    implementation("io.netty:netty-transport-native-epoll:linux-x86_64")
    implementation("io.netty:netty-transport-native-kqueue:osx-x86_64")

    jmh("org.openjdk.jmh:jmh-core:${rootProject.ext.get("jmhVersion")}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${rootProject.ext.get("jmhVersion")}")
}

jmh {
    jmhVersion.set(rootProject.ext.get("jmhVersion").toString())
    warmupIterations.set(1)
    iterations.set(1)
    resultFormat.set("json")
    benchmarkMode.set(listOf(
        "thrpt"
    ))
    threads.set(50)
    fork.set(1)
}
