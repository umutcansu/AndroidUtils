package com.androidutil.util

import java.util.concurrent.TimeUnit

object ProcessRunner {

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    fun run(
        command: List<String>,
        timeoutSeconds: Long = 120,
        workingDir: java.io.File? = null
    ): Result {
        val pb = ProcessBuilder(command)
            .redirectErrorStream(false)

        if (workingDir != null) {
            pb.directory(workingDir)
        }

        val process = pb.start()

        // Read streams in separate threads to avoid deadlock
        var stdoutText = ""
        var stderrText = ""

        val stdoutThread = Thread {
            stdoutText = process.inputStream.bufferedReader().readText()
        }
        val stderrThread = Thread {
            stderrText = process.errorStream.bufferedReader().readText()
        }

        stdoutThread.start()
        stderrThread.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after ${timeoutSeconds}s: ${command.first()}")
        }

        stdoutThread.join(5000)
        stderrThread.join(5000)

        return Result(
            exitCode = process.exitValue(),
            stdout = stdoutText,
            stderr = stderrText
        )
    }
}
