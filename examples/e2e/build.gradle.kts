// The examples and the server as separate processes, each on its real runtime classpath: what only a test classpath
// happens to hold cannot help here.
val runnables = mapOf(
    "server" to ":server",
    "provider" to ":examples:provider",
    "consumer" to ":examples:consumer",
)

runnables.values.forEach { evaluationDependsOn(it) }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("me.ahoo.test:fluent-assert-core")
    testImplementation("com.google.code.gson:gson")
}

tasks.withType<Test> {
    runnables.forEach { (name, path) ->
        val classpath = project(path).extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
        inputs.files(classpath)
        doFirst { systemProperty("cosky.classpath.$name", classpath.asPath) }
    }
    systemProperty("cosky.logs", layout.buildDirectory.dir("e2e").get().asFile.path)
}
