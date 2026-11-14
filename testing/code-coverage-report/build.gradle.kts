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

plugins {
    base
    id("jacoco-report-aggregation")
}

@Suppress("UNCHECKED_CAST")
val libraryProjects = rootProject.ext.get("libraryProjects") as Iterable<Project>

dependencies {
    libraryProjects.forEach {
        jacocoAggregation(it)
    }
}

reporting {
    reports {
        val codeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

/**
 * Not held to the coverage floor: code generated from the protos, and the test suites other modules run.
 */
val uncoveredClasses = listOf("me/ahoo/cosky/api/v1/**", "me/ahoo/cosky/test/**")

val codeCoverageReport = tasks.named<JacocoReport>("codeCoverageReport") {
    val aggregatedClasses = files(classDirectories.from.toList())
    classDirectories.setFrom(aggregatedClasses.asFileTree.matching { exclude(uncoveredClasses) })
}

/**
 * Fails the build when the aggregated coverage drops below the floor, so coverage is controlled locally and in CI
 * alike, not only reported.
 */
val codeCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    executionData.from(codeCoverageReport.map { it.executionData })
    classDirectories.from(codeCoverageReport.map { it.classDirectories })
    sourceDirectories.from(codeCoverageReport.map { it.sourceDirectories })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(codeCoverageReport, codeCoverageVerification)
}
