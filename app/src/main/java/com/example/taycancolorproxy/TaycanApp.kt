package com.example.taycancolorproxy

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class TaycanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val file = File(getExternalFilesDir(null), "crash_log.txt")
                file.writeText(sw.toString())
            } catch (e: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
