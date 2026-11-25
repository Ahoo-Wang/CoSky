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

package me.ahoo.cosky.server

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.boot.convert.DurationStyle
import org.springframework.util.unit.DataSize
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * The metadata IDEs read describes every setting of [CoSkyProperties], with the defaults the code has, and the server's
 * section of `wiki/guide/properties.md` lists the same `cosky.*` properties, in English and in Chinese.
 */
class ConfigurationMetadataTest {
    private val metadata: Map<String, JsonObject> = javaClass.classLoader.getResources(METADATA).toList()
        .map { url -> url.openStream().use { JsonParser.parseString(it.readAllBytes().decodeToString()).asJsonObject } }
        // Spring's own jars have theirs.
        .single { json -> json.getAsJsonArray("groups")?.any { it.asJsonObject["type"]?.asString == TYPE } == true }
        .getAsJsonArray("properties")
        .map { it.asJsonObject }
        .associateBy { it["name"].asString }

    @Test
    fun `every setting is described, and nothing else`() {
        metadata.keys.assert().containsExactlyInAnyOrderElementsOf(settingsOf(CoSkyProperties::class, PREFIX))
    }

    @Test
    fun `a described default is the one a setting has`() {
        metadata.forEach { (name, property) ->
            val default = property["defaultValue"] ?: return@forEach
            val value = valueOf(CoSkyProperties(), name.removePrefix("$PREFIX."))
            matches(value, default).assert().describedAs(name).isTrue()
        }
    }

    @Test
    fun `the reference lists what is described, with the same defaults, in both languages`() {
        listOf(REFERENCE, REFERENCE_ZH).forEach { reference ->
            val documented = serverRows(Files.readString(reference))
            documented.keys.assert().describedAs("$reference").containsExactlyInAnyOrderElementsOf(metadata.keys)
            metadata.forEach { (name, property) ->
                property["defaultValue"]?.let { documented[name].assert().describedAs(name).isEqualTo(rendered(it)) }
            }
        }
    }

    /**
     * The settings a class binds through its constructor, by their names in properties; a nested class of settings is
     * a prefix rather than a setting.
     */
    private fun settingsOf(type: KClass<*>, prefix: String): List<String> =
        checkNotNull(type.primaryConstructor).parameters.flatMap { parameter ->
            val name = "$prefix.${kebab(checkNotNull(parameter.name))}"
            val nested = parameter.type.jvmErasure
            if (nested.java.name.startsWith("$TYPE$")) settingsOf(nested, name) else listOf(name)
        }

    private fun valueOf(target: Any, path: String): Any? {
        val head = path.substringBefore('.')
        val property = target::class.memberProperties.single { kebab(it.name) == head }
        val value = property.getter.call(target)
        return if ('.' in path) valueOf(checkNotNull(value), path.substringAfter('.')) else value
    }

    private fun matches(value: Any?, default: JsonElement): Boolean = when (value) {
        is Duration -> DurationStyle.detectAndParse(default.asString) == value
        is DataSize -> DataSize.parse(default.asString) == value
        is List<*> -> default.asJsonArray.map { it.asString } == value
        is Boolean -> default.asBoolean == value
        is Int -> default.asInt == value
        is Double -> default.asDouble == value
        else -> default.asString == value
    }

    private companion object {
        const val METADATA = "META-INF/spring-configuration-metadata.json"
        const val PREFIX = "cosky"
        const val TYPE = "me.ahoo.cosky.server.CoSkyProperties"

        /** Tests run in the module's directory. */
        val REFERENCE: Path = Path.of("../wiki/guide/properties.md")
        val REFERENCE_ZH: Path = Path.of("../wiki/zh/guide/properties.md")

        fun kebab(name: String): String = name.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }

        /** The default as the reference writes it, in code font. */
        fun rendered(default: JsonElement): String =
            if (default.isJsonArray) default.asJsonArray.joinToString(", ") { it.asString } else default.asString

        /**
         * The `cosky.*` properties of the server's section, with their defaults as written, without the code font. The
         * section also lists Spring's own.
         */
        fun serverRows(reference: String): Map<String, String> =
            rows(reference.substringAfter("\n## Server").substringBefore("\n## Spring Boot starter"))
                .filterKeys { it.startsWith("$PREFIX.") }

        fun rows(section: String): Map<String, String> = section.lines()
            .filter { it.startsWith("| `") }
            .flatMap { row ->
                val cells = row.split('|').map { it.trim() }
                Regex("`([^`]+)`").findAll(cells[1]).map { it.groupValues[1] to cells[2].removeSurrounding("`") }
            }
            .toMap()
    }
}
