package com.example.taycancolorproxy

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var file: File? = null

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.getExternalFilesDir(null), "journal.txt")
    }

    @Synchronized
    fun log(msg: String) {
        try {
            val f = file ?: return
            val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            f.appendText("$t $msg\n")
            if (f.length() > 300000) {
                val all = f.readText()
                f.writeText(all.takeLast(150000))
            }
        } catch (e: Exception) {
        }
    }

    fun read(): String {
        return try {
            val f = file
            if (f != null && f.exists()) f.readText().takeLast(15000) else "Journal vide"
        } catch (e: Exception) {
            "Erreur de lecture"
        }
    }

    fun clear() {
        try {
            file?.delete()
        } catch (e: Exception) {
        }
    }
}
