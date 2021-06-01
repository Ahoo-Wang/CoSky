/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/7.0/userguide/multi_project_builds.html
 */
rootProject.name = "cosky"

include(":cosky-core")
include(":cosky-config")
include(":cosky-discovery")
include(":cosky-bom")
include(":cosky-dependencies")
include(":cosky-spring-cloud-core")
include(":spring-cloud-starter-cosky-discovery")
include(":spring-cloud-starter-cosky-config")
include(":cosky-rest-api")
include(":cosky-mirror")

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.30.0")
    }
}
