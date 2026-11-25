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

package me.ahoo.cosky.migration

import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.migration.MigrationEnvironment.REDIS
import me.ahoo.cosky.migration.MigrationEnvironment.REDIS_URI
import me.ahoo.cosky.migration.MigrationEnvironment.TOKEN
import me.ahoo.cosky.migration.MigrationEnvironment.URL
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * The format of a Config whose name has no extension is each run's choice (`--default-format`). A run that chooses
 * another one than the run before gives it that format, with the care it takes over a content: never in place of a
 * Config edited on 8.0.
 */
class DefaultFormatTest {
    @TempDir
    lateinit var directory: Path

    private val six = SixRedis(REDIS)
    private val target = RestTarget(URL, TOKEN)
    private val namespace = "ns-${UUID.randomUUID().toString().take(8)}"
    private val source = "cosky-{$namespace}"

    private val report: String
        get() = Files.readString(directory.resolve("report.md"))

    @BeforeEach
    fun start() {
        REDIS.flushall()
        six.listNamespace(source)
    }

    private fun run(vararg arguments: String): Int = migrate(
        listOf("--source", REDIS_URI, "--target", URL, "--report", directory.resolve("report.md").toString()) +
            arguments,
        mapOf(Options.TOKEN_VARIABLE to TOKEN),
        PrintStream(ByteArrayOutputStream()),
    )

    private fun configOf(name: String): Resource = checkNotNull(target.config(namespace, name))

    @Test
    fun `running it again with another default format changes the format, and says so`() {
        six.setConfig(source, "order-service", "a=1")
        six.setConfig(source, "app.yaml", "a: 1")
        run().assert().isEqualTo(EXIT_DONE)
        val first = configOf("order-service")
        first.config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)

        run("--default-format", "properties", "--dry-run").assert().isEqualTo(EXIT_DONE)
        report.assert().contains("| `$source` | `order-service` | `yaml` | `properties` |")
        configOf("order-service").assert().isEqualTo(first)

        run("--default-format", "properties").assert().isEqualTo(EXIT_DONE)

        val changed = configOf("order-service")
        changed.config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_PROPERTIES)
        changed.config.content.assert().isEqualTo("a=1")
        configOf("app.yaml").config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)
        report.assert().contains("| `$namespace` | 0 | 1 | 1 | 0 |")
            .contains("### Formats changed")
            .contains("| `$source` | `order-service` | `yaml` | `properties` |")
            .doesNotContain("`app.yaml` | `")

        run("--default-format", "properties").assert().isEqualTo(EXIT_DONE)

        configOf("order-service").meta.revision.assert().isEqualTo(changed.meta.revision)
        report.assert().contains("| `$namespace` | 0 | 0 | 2 | 0 |").doesNotContain("Formats changed")
    }

    @Test
    fun `a history whose content came back still fits once its format changed`() {
        listOf("A", "B", "A").forEach { six.setConfig(source, "order-service", it) }
        run("--history").assert().isEqualTo(EXIT_DONE)
        run("--history", "--default-format", "text").assert().isEqualTo(EXIT_DONE)

        run("--history", "--default-format", "text").assert().isEqualTo(EXIT_DONE)

        report.assert().contains("| `$namespace` | 0 | 0 | 1 | 0 |")
        configOf("order-service").config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_TEXT)
    }

    @Test
    fun `what was edited on 8_0 keeps its format`() {
        six.setConfig(source, "order-service", "a=1")
        six.setConfig(source, "app.yaml", "a: 1")
        run().assert().isEqualTo(EXIT_DONE)
        val edited = configOf("order-service")
        target.putConfig(namespace, "order-service", text("edited"), edited.meta.revision)
        // A name with an extension has the same format in every run, so another one was chosen on 8.0.
        target.putConfig(namespace, "app.yaml", text("a: 1"), configOf("app.yaml").meta.revision)

        run("--default-format", "properties").assert().isEqualTo(EXIT_ATTENTION)

        val kept = configOf("order-service").config
        kept.content.assert().isEqualTo("edited")
        kept.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_TEXT)
        configOf("app.yaml").config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_TEXT)
        report.assert().contains("`order-service` | CONFLICT").doesNotContain("Formats changed")
    }

    private fun text(content: String): Resource = resource {
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            this.content = content
        }
    }
}
