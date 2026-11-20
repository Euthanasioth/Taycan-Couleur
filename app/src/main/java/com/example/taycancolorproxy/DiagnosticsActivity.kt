
package com.example.taycancolorproxy

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : Activity() {

    private lateinit var layout: LinearLayout
    private lateinit var journalText: TextView
    private lateinit var crashText: TextView
    private lateinit var exitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Journal & diagnostics"
            textSize = 22f
            setPadding(0, 0, 0, 35)
        }
        layout.addView(title)

        val backButton = Button(this).apply {
            text = "← Retour à l'accueil"
            setOnClickListener { finish() }
        }
        layout.addView(backButton)

        // Journal de l'application
        addSectionTitle("JOURNAL DE L'APPLICATION")

        journalText = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 10, 0, 20)
        }
        layout.addView(journalText)

        val refreshJournalButton = Button(this).apply {
            text = "Actualiser le journal"
            setOnClickListener { refreshJournal() }
        }
        layout.addView(refreshJournalButton)

        val clearJournalButton = Button(this).apply {
            text = "Effacer le journal"
            setOnClickListener {
                Logger.clear()
                refreshJournal()
            }
        }
        layout.addView(clearJournalButton)

        // Dernier crash
        addSectionTitle("DERNIER CRASH")

        crashText = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 10, 0, 20)
        }
        layout.addView(crashText)

        val clearCrashButton = Button(this).apply {
            text = "Effacer le journal de crash"
            setOnClickListener {
                crashFile().delete()
                refreshCrash()
            }
        }
        layout.addView(clearCrashButton)

        // Historique des morts du processus
        addSectionTitle("DERNIÈRES MORTS DU PROCESSUS")

        exitText = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 10, 0, 20)
        }
        layout.addView(exitText)

        val refreshExitButton = Button(this).apply {
            text = "Actualiser les diagnostics"
            setOnClickListener { refreshExitInfo() }
        }
        layout.addView(refreshExitButton)

        scroll.addView(layout)
        setContentView(scroll)

        refreshAll()
    }

    private fun addSectionTitle(value: String) {
        val title = TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, 45, 0, 5)
        }
        layout.addView(title)
    }

    private fun crashFile(): File {
        return File(getExternalFilesDir(null), "crash_log.txt")
    }

    private fun refreshAll() {
        refreshJournal()
        refreshCrash()
        refreshExitInfo()
    }

    private fun refreshJournal() {
        journalText.text = Logger.read()
    }

    private fun refreshCrash() {
        val file = crashFile()
        crashText.text = if (file.exists()) {
            file.readText()
        } else {
            "Aucun crash enregistré pour le moment."
        }
    }

    private fun refreshExitInfo() {
        exitText.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val list = am.getHistoricalProcessExitReasons(packageName, 0, 3)

                if (list.isEmpty()) {
                    "Aucune mort de processus enregistrée."
                } else {
                    list.joinToString("\n\n") {
                        val date = SimpleDateFormat(
                            "dd/MM HH:mm:ss",
                            Locale.FRANCE
                        ).format(Date(it.timestamp))

                        "$date  raison=${it.reason}  importance=${it.importance}\n${it.description ?: ""}"
                    }
                }
            } catch (e: Exception) {
                "Lecture impossible : ${e.message}"
            }
        } else {
            "Non disponible (Android < 11)"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::journalText.isInitialized) refreshAll()
    }
}
