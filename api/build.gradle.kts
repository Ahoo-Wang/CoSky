/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import me.ahoo.cosky.build.GenerateOpenApi

plugins {
    alias(libs.plugins.protobuf)
}

dependencies {
    api("com.google.protobuf:protobuf-java")
    api("com.google.protobuf:protobuf-kotlin")
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-kotlin-stub")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.yaml:snakeyaml")
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.grpc.protoc.gen.java.get().toString()
        }
        create("grpckt") {
            artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                // Omit javax.annotation.Generated, which is not on the Java 17 classpath.
                create("grpc") {
                    option("@generated=omit")
                }
                create("grpckt")
            }
            builtins {
                create("kotlin")
            }
        }
        ofSourceSet("main").configureEach {
            // Input of the OpenAPI schemas; source info carries the proto comments into the descriptions.
            generateDescriptorSet = true
            descriptorSetOptions.includeSourceInfo = true
            descriptorSetOptions.includeImports = true
            descriptorSetOptions.path = descriptorSetFile.get().asFile.path
        }
    }
}

val descriptorSetFile: Provider<RegularFile>
    get() = layout.buildDirectory.file("descriptors/main.desc")

/*
 * OpenAPI document for the REST API (design D18): schemas generated from the protos, paths written by hand in
 * src/main/openapi/paths.yaml. Part of `assemble`; fails on unresolved references.
 */
val generateOpenApi by tasks.registering(GenerateOpenApi::class) {
    group = "build"
    description = "Generates build/openapi/cosky.yaml from the protos and src/main/openapi/paths.yaml."
    dependsOn("generateProto")
    descriptorSet = descriptorSetFile
    paths = layout.projectDirectory.file("src/main/openapi/paths.yaml")
    protoPackage = "cosky.v1"
    output = layout.buildDirectory.file("openapi/cosky.yaml")
}

tasks.assemble {
    dependsOn(generateOpenApi)
}

tasks.test {
    inputs.files(generateOpenApi)
    systemProperty("cosky.openapi", generateOpenApi.get().output.get().asFile.path)
}

tasks.withType<Javadoc> {
    // Generated protobuf and gRPC sources do not follow doclint rules.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

/*
 * `buf lint` checks the protos against the rules in src/main/proto/buf.yaml. The buf binary comes from Maven Central,
 * so the check needs no local installation.
 */
val buf by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    buf("${libs.buf.get()}:${bufClassifier()}@exe")
}

val bufLint by tasks.registering(Exec::class) {
    group = "verification"
    description = "Lints the protos with buf."
    val protoDir = layout.projectDirectory.dir("src/main/proto")
    val bufExecutable = buf.elements.map { it.single().asFile }
    inputs.files(buf)
    inputs.dir(protoDir)
    val marker = layout.buildDirectory.file("buf/lint.ok")
    outputs.file(marker)
    workingDir(protoDir)
    doFirst {
        bufExecutable.get().setExecutable(true)
        executable = bufExecutable.get().absolutePath
    }
    args("lint", "--error-format", "text")
    doLast {
        marker.get().asFile.writeText("ok")
    }
}

tasks.check {
    dependsOn(bufLint)
}

fun bufClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = when (System.getProperty("os.arch").lowercase()) {
        "aarch64", "arm64" -> "aarch_64"
        else -> "x86_64"
    }
    return when {
        os.contains("mac") -> "osx-$arch"
        os.contains("windows") -> "windows-$arch"
        else -> "linux-$arch"
    }
}
