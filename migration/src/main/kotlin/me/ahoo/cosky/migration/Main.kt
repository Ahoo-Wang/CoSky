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

import io.lettuce.core.RedisException
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import kotlin.system.exitProcess

internal const val EXIT_DONE = 0
internal const val EXIT_ATTENTION = 1
internal const val EXIT_UNABLE = 2

fun main(args: Array<String>) {
    exitProcess(migrate(args.asList(), System.getenv(), System.out))
}

/**
 * @return the exit code.
 */
internal fun migrate(args: List<String>, environment: Map<String, String>, out: PrintStream): Int = try {
    val options = Options.parse(args, environment)
    if (options == null) {
        out.println(Options.USAGE)
        EXIT_DONE
    } else {
        migrate(options, out)
    }
} catch (e: UsageException) {
    out.println(e.message)
    EXIT_UNABLE
} catch (e: RedisException) {
    out.println("Cannot read the 6.x Redis: ${e.message}")
    EXIT_UNABLE
} catch (e: IOException) {
    out.println("Cannot write the report: ${e.message}")
    EXIT_UNABLE
}

private fun migrate(options: Options, out: PrintStream): Int {
    val target = RestTarget(options.target, options.token)
    return SourceConnection.open(options).use { connection ->
        val report = MigrationReport(connection.label, options.target, options.history, options.dryRun)
        try {
            Migration(Source(connection.commands), target, options, report).run()
        } catch (e: TargetStoppedException) {
            report.stopped = e.message
        } catch (e: RedisException) {
            report.stopped = "Cannot read the 6.x Redis: ${e.message}"
        }
        options.report.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(options.report, report.render())
        report.summary().forEach(out::println)
        report.stopped?.let { out.println("Stopped early: $it") }
        out.println("Report: ${options.report}")
        when {
            report.stopped != null -> EXIT_UNABLE
            report.needsAttention -> EXIT_ATTENTION
            else -> EXIT_DONE
        }
    }
}
