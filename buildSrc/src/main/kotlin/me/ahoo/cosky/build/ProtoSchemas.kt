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

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet

/**
 * Turns the messages and enums of one proto package into OpenAPI 3.1 schemas following the proto3 JSON mapping:
 * lowerCamelCase property names, 64-bit integers as strings, enums as their value names, maps as objects,
 * `google.protobuf.Timestamp` as an RFC 3339 string. Leading and trailing proto comments become descriptions.
 */
class ProtoSchemas(private val descriptorSet: FileDescriptorSet, private val protoPackage: String) {
    private val mapEntries: Set<String> = descriptorSet.fileList.flatMap { file ->
        file.messageTypeList.flatMap { collectMapEntries(".${file.`package`}", it) }
    }.toSet()

    /** Schema name (type name without the package, nested types joined by `.`) to schema. */
    fun schemas(): Map<String, Any> {
        val schemas = sortedMapOf<String, Any>()
        descriptorSet.fileList.filter { it.`package` == protoPackage }.forEach { file ->
            val comments = Comments(file)
            file.messageTypeList.forEachIndexed { index, message ->
                message(schemas, "", message, listOf(MESSAGE_TYPE, index), comments)
            }
            file.enumTypeList.forEachIndexed { index, enum ->
                schemas[enum.name] = enum(enum, listOf(ENUM_TYPE, index), comments)
            }
        }
        return schemas
    }

    private fun message(
        schemas: MutableMap<String, Any>,
        parent: String,
        message: DescriptorProto,
        path: List<Int>,
        comments: Comments,
    ) {
        if (message.options.mapEntry) {
            return
        }
        val name = parent + message.name
        val properties = linkedMapOf<String, Any>()
        message.fieldList.forEachIndexed { index, field ->
            val schema = field(field).toMutableMap()
            comments.of(path + listOf(FIELD, index))?.let { schema["description"] = it }
            properties[field.jsonName] = schema
        }
        val schema = linkedMapOf<String, Any>("type" to "object")
        description(message, comments.of(path))?.let { schema["description"] = it }
        if (properties.isNotEmpty()) {
            schema["properties"] = properties
        }
        schemas[name] = schema
        message.nestedTypeList.forEachIndexed { index, nested ->
            message(schemas, "$name.", nested, path + listOf(NESTED_TYPE, index), comments)
        }
        message.enumTypeList.forEachIndexed { index, enum ->
            schemas["$name.${enum.name}"] = enum(enum, path + listOf(NESTED_ENUM, index), comments)
        }
    }

    /** Members of a real (non-synthetic) oneof are documented on the message, since JSON has no oneof. */
    private fun description(message: DescriptorProto, comment: String?): String? {
        val oneofs = message.oneofDeclList.mapIndexedNotNull { index, oneof ->
            val members = message.fieldList.filter {
                it.hasOneofIndex() && it.oneofIndex == index && !it.proto3Optional
            }
            members.takeIf { it.isNotEmpty() }?.let {
                "At most one of ${it.joinToString { field -> "`${field.jsonName}`" }} is set (oneof `${oneof.name}`)."
            }
        }
        val parts = listOfNotNull(comment) + oneofs
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun field(field: FieldDescriptorProto): Map<String, Any> {
        val repeated = field.label == FieldDescriptorProto.Label.LABEL_REPEATED
        if (repeated && field.typeName in mapEntries) {
            val entry = findMessage(field.typeName)
            return mapOf("type" to "object", "additionalProperties" to single(entry.fieldList[1]))
        }
        val single = single(field)
        return if (repeated) mapOf("type" to "array", "items" to single) else single
    }

    private fun single(field: FieldDescriptorProto): Map<String, Any> = when (field.type) {
        Type.TYPE_INT32, Type.TYPE_SINT32, Type.TYPE_SFIXED32 -> mapOf("type" to "integer", "format" to "int32")
        Type.TYPE_UINT32, Type.TYPE_FIXED32 -> mapOf("type" to "integer", "format" to "int64", "minimum" to 0)
        Type.TYPE_INT64, Type.TYPE_SINT64, Type.TYPE_SFIXED64 -> mapOf("type" to "string", "format" to "int64")
        Type.TYPE_UINT64, Type.TYPE_FIXED64 -> mapOf("type" to "string", "format" to "uint64")
        Type.TYPE_FLOAT -> mapOf("type" to "number", "format" to "float")
        Type.TYPE_DOUBLE -> mapOf("type" to "number", "format" to "double")
        Type.TYPE_BOOL -> mapOf("type" to "boolean")
        Type.TYPE_STRING -> mapOf("type" to "string")
        Type.TYPE_BYTES -> mapOf("type" to "string", "format" to "byte")
        Type.TYPE_ENUM, Type.TYPE_MESSAGE -> reference(field.typeName)
        else -> error("Unsupported field type ${field.type} of ${field.name}.")
    }

    private fun reference(typeName: String): Map<String, Any> {
        WELL_KNOWN[typeName]?.let { return it }
        val prefix = ".$protoPackage."
        require(typeName.startsWith(prefix)) { "Type $typeName is outside $protoPackage and not supported." }
        return mapOf("\$ref" to "#/components/schemas/${typeName.removePrefix(prefix)}")
    }

    private fun enum(enum: EnumDescriptorProto, path: List<Int>, comments: Comments): Map<String, Any> {
        val schema = linkedMapOf<String, Any>("type" to "string")
        val values = enum.valueList.mapIndexed { index, value ->
            val comment = comments.of(path + listOf(ENUM_VALUE, index))
            if (comment == null) "- `${value.name}`" else "- `${value.name}`: $comment"
        }
        schema["description"] = (listOfNotNull(comments.of(path)) + values.joinToString("\n")).joinToString("\n\n")
        schema["enum"] = enum.valueList.map { it.name }
        return schema
    }

    private fun findMessage(typeName: String): DescriptorProto {
        descriptorSet.fileList.forEach { file ->
            file.messageTypeList.forEach { message ->
                find(".${file.`package`}", message, typeName)?.let { return it }
            }
        }
        error("Message $typeName not found.")
    }

    private fun find(parent: String, message: DescriptorProto, typeName: String): DescriptorProto? {
        val name = "$parent.${message.name}"
        if (name == typeName) {
            return message
        }
        return message.nestedTypeList.firstNotNullOfOrNull { find(name, it, typeName) }
    }

    private fun collectMapEntries(parent: String, message: DescriptorProto): List<String> {
        val name = "$parent.${message.name}"
        val own = if (message.options.mapEntry) listOf(name) else emptyList()
        return own + message.nestedTypeList.flatMap { collectMapEntries(name, it) }
    }

    /** Comments by source path, from `source_code_info`. */
    private class Comments(file: FileDescriptorProto) {
        private val byPath: Map<List<Int>, String> = file.sourceCodeInfo.locationList.mapNotNull { location ->
            val text = listOf(location.leadingComments, location.trailingComments)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            if (text.isEmpty()) null else location.pathList.toList() to text
        }.toMap()

        fun of(path: List<Int>): String? = byPath[path]
    }

    private companion object {
        // Field numbers in descriptor.proto, used in source_code_info paths.
        const val MESSAGE_TYPE = 4
        const val ENUM_TYPE = 5
        const val FIELD = 2
        const val NESTED_TYPE = 3
        const val NESTED_ENUM = 4
        const val ENUM_VALUE = 2

        val WELL_KNOWN: Map<String, Map<String, Any>> = mapOf(
            ".google.protobuf.Timestamp" to mapOf("type" to "string", "format" to "date-time"),
            ".google.protobuf.Duration" to mapOf("type" to "string", "pattern" to "^-?[0-9]+(\\.[0-9]{1,9})?s$"),
            ".google.protobuf.Empty" to mapOf("type" to "object"),
        )
    }
}
