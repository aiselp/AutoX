package com.aiselp.autox.utils

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

object LogCat {

    fun readLogcat(): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        dumpLogcat(byteArrayOutputStream)
        return String(byteArrayOutputStream.toByteArray())
    }

    fun saveLogcat(file: File) {
        val commandLine = CommandLine("logcat").apply {
            addArguments("-d -f")
            addArgument(file.absolutePath)
        }
        DefaultExecutor.Builder().get().execute(commandLine)
    }

    fun dumpLogcat(outputStream: OutputStream) {
        val commandLine = CommandLine("logcat").apply {
            addArgument("-d")
        }
        DefaultExecutor.Builder().get().apply {
            streamHandler = PumpStreamHandler(outputStream)
            execute(commandLine)
        }
    }
}