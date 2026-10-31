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
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Violation

/**
 * The proto3 JSON mapping of the REST API (design §4.5).
 */
internal object ProtoJson {
    private val printer = JsonFormat.printer().omittingInsignificantWhitespace()
    private val parser = JsonFormat.parser().ignoringUnknownFields()

    fun print(message: Message): String = printer.print(message)

    fun tree(message: Message): JsonElement = JsonParser.parseString(print(message))

    /**
     * @throws CoSkyException with `INVALID_ARGUMENT` when [json] does not fit.
     */
    fun <B : Message.Builder> parse(json: String, builder: B): B {
        try {
            parser.merge(json, builder)
        } catch (@Suppress("SwallowedException") e: InvalidProtocolBufferException) {
            throw invalidBody(e.message.orEmpty())
        }
        return builder
    }

    /**
     * @throws CoSkyException with `INVALID_ARGUMENT` unless [json] is an object.
     */
    fun objectOf(json: String): JsonObject = try {
        JsonParser.parseString(json).asJsonObject
    } catch (@Suppress("SwallowedException") e: JsonParseException) {
        throw invalidBody(e.message.orEmpty())
    } catch (@Suppress("SwallowedException") e: IllegalStateException) {
        throw invalidBody(e.message.orEmpty())
    }

    fun invalidBody(description: String): CoSkyException = invalid("body", description)

    fun invalid(field: String, description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(field, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
    )
}
