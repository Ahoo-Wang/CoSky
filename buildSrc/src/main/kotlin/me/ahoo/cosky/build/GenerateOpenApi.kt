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
package me.ahoo.cosky.build

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer

/**
 * Builds the OpenAPI document (design D18): `components.schemas` generated from the proto descriptor set, merged with
 * the hand-written document (info, paths, other components). Fails on a schema defined twice or a `$ref` that does
 * not resolve.
 */
@CacheableTask
abstract class GenerateOpenApi : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val descriptorSet: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val paths: RegularFileProperty

    @get:Input
    abstract val protoPackage: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun generate() {
        val descriptors = descriptorSet.get().asFile.inputStream().use { FileDescriptorSet.parseFrom(it) }
        val generated = ProtoSchemas(descriptors, protoPackage.get()).schemas()
        @Suppress("UNCHECKED_CAST")
        val document = paths.get().asFile.reader().use { yaml().load<Any>(it) } as MutableMap<String, Any?>
        val merged = OpenApiMerger.merge(document, generated)
        val unresolved = OpenApiMerger.unresolvedRefs(merged)
        if (unresolved.isNotEmpty()) {
            throw GradleException("Unresolved \$ref in the OpenAPI document: ${unresolved.joinToString()}")
        }
        output.get().asFile.apply { parentFile.mkdirs() }.writer().use { yaml().dump(merged, it) }
    }

    private fun yaml(): Yaml {
        val dumper = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            width = Int.MAX_VALUE
            splitLines = false
        }
        return Yaml(SafeConstructor(LoaderOptions()), Representer(dumper), dumper)
    }
}

/**
 * Pure document operations, kept apart from the task.
 */
object OpenApiMerger {
    @Suppress("UNCHECKED_CAST")
    fun merge(document: MutableMap<String, Any?>, generated: Map<String, Any>): Map<String, Any?> {
        val components = (document.getOrPut("components") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>)
        val handWritten = (components["schemas"] as Map<String, Any?>?).orEmpty()
        val duplicates = handWritten.keys.intersect(generated.keys)
        if (duplicates.isNotEmpty()) {
            throw GradleException("Schemas both generated from protos and written by hand: $duplicates")
        }
        components["schemas"] = (generated + handWritten).toSortedMap()
        return document
    }

    fun unresolvedRefs(document: Map<String, Any?>): List<String> =
        refs(document).distinct().filter { resolve(document, it) == null }.sorted()

    private fun refs(node: Any?): List<String> = when (node) {
        is Map<*, *> -> node.flatMap { (key, value) ->
            if (key == "\$ref" && value is String) listOf(value) else refs(value)
        }

        is List<*> -> node.flatMap { refs(it) }
        else -> emptyList()
    }

    private fun resolve(document: Map<String, Any?>, ref: String): Any? {
        if (!ref.startsWith("#/")) {
            return null
        }
        return ref.removePrefix("#/").split('/').fold(document as Any?) { node, token ->
            (node as? Map<*, *>)?.get(token.replace("~1", "/").replace("~0", "~"))
        }
    }
}
