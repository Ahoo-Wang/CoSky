/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java library project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.0/userguide/building_java_projects.html
 */
plugins {
    id("io.codearte.nexus-staging")
}

val bomProjects = listOf(
    project(":cosky-bom"),
    project(":cosky-dependencies")
)
val coreProjects = listOf(
    project(":cosky-config"),
    project(":cosky-discovery")
)
val restApiProject = project(":cosky-rest-api")
val mirrorProject = project(":cosky-mirror")
val serverProjects = listOf(restApiProject, mirrorProject)
val exampleProjects = listOf(
    project(":cosky-service-provider"),
    project(":cosky-service-provider-api"),
    project(":cosky-service-consumer")
)
val publishProjects = subprojects - serverProjects - exampleProjects
val libraryProjects = publishProjects - bomProjects

ext {
    set("lombokVersion", "1.18.20")
    set("springBootVersion", "2.4.7")
    set("springCloudVersion", "2020.0.3")
    set("jmhVersion", "1.29")
    set("guavaVersion", "30.0-jre")
    set("commonsIOVersion", "2.10.0")
    set("springfoxVersion", "3.0.0")
    set("metricsVersion", "4.2.0")
    set("jjwtVersion", "0.11.2")
    set("cosIdVersion", "1.3.1")
    set("libraryProjects", libraryProjects)
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

configure(bomProjects) {
    apply<JavaPlatformPlugin>()
    configure<JavaPlatformExtension> {
        allowDependencies()
    }
}

configure(libraryProjects) {
    apply<JavaLibraryPlugin>()
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val depLombok = "org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}"
        this.add("api", platform(project(":cosky-dependencies")))
        this.add("compileOnly", depLombok)
        this.add("annotationProcessor", depLombok)
        this.add("testCompileOnly", depLombok)
        this.add("testAnnotationProcessor", depLombok)
        this.add("implementation", "com.google.guava:guava")
        this.add("implementation", "org.slf4j:slf4j-api")
        this.add("testImplementation", "ch.qos.logback:logback-classic")
        this.add("testImplementation", "org.junit.jupiter:junit-jupiter-api")
        this.add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")
    }
}

configure(publishProjects) {
    val isBom = bomProjects.contains(this)
    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "projectBuildRepo"
                url = uri(layout.buildDirectory.dir("repos"))
            }
            maven {
                name = "sonatypeRepo"
                url = if (version.toString().endsWith("SNAPSHOT"))
                    uri("https://oss.sonatype.org/content/repositories/snapshots")
                else
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")

                credentials {
                    username = getPropertyOf("ossrhUsername")
                    password = getPropertyOf("ossrhPassword")
                }
            }
        }
        publications {
            val publishName = if (isBom) "mavenBom" else "mavenLibrary"
            val publishComponentName = if (isBom) "javaPlatform" else "java"
            create<MavenPublication>(publishName) {
                from(components[publishComponentName])
                pom {
                    name.set(rootProject.name)
                    description.set(getPropertyOf("description"))
                    url.set(getPropertyOf("website"))
                    issueManagement {
                        system.set("GitHub")
                        url.set(getPropertyOf("issues"))
                    }
                    scm {
                        url.set(getPropertyOf("website"))
                        connection.set(getPropertyOf("vcs"))
                    }
                    licenses {
                        license {
                            name.set(getPropertyOf("license_name"))
                            url.set(getPropertyOf("license_url"))
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("ahoo-wang")
                            name.set("ahoo wang")
                            organization {
                                url.set(getPropertyOf("website"))
                            }
                        }
                    }
                }
            }
        }
    }
    configure<SigningExtension> {
        if (isBom) {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenBom"))
        } else {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenLibrary"))
        }
    }
}

nexusStaging {
    username = getPropertyOf("ossrhUsername")
    password = getPropertyOf("ossrhPassword")
    packageGroup = "me.ahoo"
}

fun getPropertyOf(name: String) = project.properties[name]?.toString()


