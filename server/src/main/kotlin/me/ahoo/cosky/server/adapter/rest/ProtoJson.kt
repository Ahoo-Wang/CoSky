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

package me.ahoo.cosky.server.adapter.rest

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Violation

/**
 * The proto3 JSON mapping of the REST API (design §4.5). Bodies are read strictly: a member the message does not have
 * is refused rather than dropped, as a misspelt one would leave its field empty, and a Put would clear it.
 *
 * A violation names its field by the proto field path in the request, as gRPC does (design §4.4).
 */
internal object ProtoJson {
    private val printer = JsonFormat.printer().omittingInsignificantWhitespace()
    private val parser = JsonFormat.parser()

    /** Where a body that cannot be read is reported when nothing more precise is known: the one a Put carries. */
    const val BODY = "resource"

    fun print(message: Message): String = printer.print(message)

    fun tree(message: Message): JsonElement = JsonParser.parseString(print(message))

    /**
     * @param field what the body sets in the request, which a body that cannot be read is reported on.
     * @param prefix the path of the message in the request, under which a member it does not have is reported.
     * @throws CoSkyException with `INVALID_ARGUMENT` when [json] does not fit.
     */
    fun <B : Message.Builder> parse(json: String, builder: B, field: String, prefix: String): B {
        val tree = treeOf(json, field)
        if (tree.isJsonObject) {
            val violations = violationsIn(tree.asJsonObject, builder.descriptorForType, prefix)
            if (violations.isNotEmpty()) {
                throw CoSkyException.invalidArgument(violations)
            }
        }
        try {
            parser.merge(json, builder)
        } catch (@Suppress("SwallowedException") e: InvalidProtocolBufferException) {
            throw invalid(field, e.message.orEmpty())
        }
        return builder
    }

    /**
     * @param field what the body sets in the request, which a body that is not an object is reported on.
     * @throws CoSkyException with `INVALID_ARGUMENT` unless [json] is an object.
     */
    fun objectOf(json: String, field: String): JsonObject =
        treeOf(json, field).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalid(field, "The body must be a JSON object.")

    private fun treeOf(json: String, field: String): JsonElement = try {
        JsonParser.parseString(json)
    } catch (@Suppress("SwallowedException") e: JsonParseException) {
        throw invalid(field, e.message.orEmpty())
    }

    /**
     * @throws CoSkyException with `INVALID_ARGUMENT` when [body] holds a member other than [members].
     */
    fun requireOnly(body: JsonObject, members: Set<String>) {
        val unknown = body.keySet() - members
        if (unknown.isNotEmpty()) {
            throw CoSkyException.invalidArgument(unknown.map { Violation(it, NOT_ALLOWED, NO_SUCH_FIELD) })
        }
    }

    fun invalidBody(description: String): CoSkyException = invalid(BODY, description)

    fun invalid(field: String, description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(field, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
    )

    fun outOfRange(field: String, description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(field, ViolationCode.VIOLATION_CODE_OUT_OF_RANGE, description)),
    )

    private val NOT_ALLOWED = ViolationCode.VIOLATION_CODE_NOT_ALLOWED
    private const val NO_SUCH_FIELD = "There is no such field."
    private const val WELL_KNOWN = "google.protobuf"

    /**
     * What the parser would let pass at any depth of [json]: a member [descriptor] has no field for, by JSON or proto
     * name, and a timestamp that is not a string, which the parser takes from an array of one. Any other value of the
     * wrong type is left to the parser.
     */
    private fun violationsIn(json: JsonObject, descriptor: Descriptor, prefix: String): List<Violation> =
        json.entrySet().flatMap { (name, value) ->
            val field = descriptor.fields.firstOrNull { it.jsonName == name || it.name == name }
            when {
                field == null -> listOf(Violation(path(prefix, name), NOT_ALLOWED, NO_SUCH_FIELD))
                field.javaType != FieldDescriptor.JavaType.MESSAGE || value.isJsonNull -> emptyList()
                // Well-known types have JSON of their own: a timestamp is a string.
                field.messageType.file.`package` == WELL_KNOWN -> wellKnown(field, value, path(prefix, field.name))
                else -> violationsIn(field, value, path(prefix, field.name))
            }
        }

    private fun wellKnown(field: FieldDescriptor, value: JsonElement, at: String): List<Violation> =
        if (field.isRepeated || (value.isJsonPrimitive && value.asJsonPrimitive.isString)) {
            emptyList()
        } else {
            listOf(Violation(at, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, "This takes a string."))
        }

    private fun violationsIn(field: FieldDescriptor, value: JsonElement, at: String): List<Violation> = when {
        field.isMapField -> {
            val values = field.messageType.findFieldByName("value")
            if (values.javaType == FieldDescriptor.JavaType.MESSAGE && value.isJsonObject) {
                value.asJsonObject.entrySet().flatMap { (key, entry) ->
                    objectsIn(entry) { violationsIn(it, values.messageType, "$at[\"$key\"]") }
                }
            } else {
                emptyList()
            }
        }

        field.isRepeated && value.isJsonArray -> value.asJsonArray.flatMapIndexed { index, element ->
            objectsIn(element) { violationsIn(it, field.messageType, "$at[$index]") }
        }

        else -> objectsIn(value) { violationsIn(it, field.messageType, at) }
    }

    private inline fun objectsIn(value: JsonElement, block: (JsonObject) -> List<Violation>): List<Violation> =
        if (value.isJsonObject) block(value.asJsonObject) else emptyList()

    private fun path(prefix: String, name: String): String = if (prefix.isEmpty()) name else "$prefix.$name"
}
