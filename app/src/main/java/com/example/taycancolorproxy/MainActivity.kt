package com.example.taycancolorproxy

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_POST_NOTIF = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DIAG : enregistre la trace exacte d'un plantage dans crash_log.txt
        val ancienHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                File(getExternalFilesDir(null), "crash_log.txt").writeText(
                    "Thread : ${thread.name}\n${android.util.Log.getStackTraceString(e)}"
                )
            } catch (_: Exception) {
            }
            ancienHandler?.uncaughtException(thread, e)
        }

        // Permission POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIF
                )
            }
        }

        val prefs: SharedPreferences = getSharedPreferences("taycan_couleur", MODE_PRIVATE)

        // DIAG : pourquoi les derniers processus sont morts ?
        val exitInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val list = am.getHistoricalProcessExitReasons(packageName, 0, 3)
                if (list.isEmpty()) {
                    "Aucune mort de processus enregistrée."
                } else {
                    list.joinToString("\n\n") {
                        val date = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.FRANCE)
                            .format(java.util.Date(it.timestamp))
                        "$date  raison=${it.reason}  importance=${it.importance}\n${it.description ?: ""}"
                    }
                }
            } catch (e: Exception) {
                "Lecture impossible : ${e.message}"
            }
        } else {
            "Non disponible (Android < 11)"
        }

        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 100, 40, 40)

        val text = TextView(this)
        text.text = "Taycan Couleur\n\nÉtape 1 : autorise l'accès aux notifications ci-dessous.\nÉtape 2 : démarre le proxy.\nÉtape 3 : choisis une couleur.\nÉtape 4 : lance Deezer et sélectionne \"Taycan Couleur\" dans Android Auto (ou une app proxy sur ton téléphone)."
        text.textSize = 16f
        layout.addView(text)

        val button = Button(this)
        button.text = "Ouvrir les réglages d'accès aux notifications"
        button.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        layout.addView(button)

        // === Déclaration de diagText AVANT les boutons qui l'utilisent ===
        val diagText = TextView(this)
        diagText.textSize = 12f

        // === Démarrer / arrêter le proxy ===
        val startProxyBtn = Button(this)
        startProxyBtn.text = "▶ Démarrer le proxy"
        startProxyBtn.setOnClickListener {
            try {
                val intent = Intent(this, ColorProxyService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                diagText.text = "Proxy démarré ✓"
            } catch (e: Exception) {
                diagText.text = "Erreur démarrage : ${e.message}"
            }
        }
        layout.addView(startProxyBtn)

        val stopProxyBtn = Button(this)
        stopProxyBtn.text = "⏹ Arrêter le proxy"
        stopProxyBtn.setOnClickListener {
            try {
                stopService(Intent(this, ColorProxyService::class.java))
                diagText.text = "Proxy arrêté"
            } catch (e: Exception) {
                diagText.text = "Erreur arrêt : ${e.message}"
            }
        }
        layout.addView(stopProxyBtn)

        // === Diagnostic des sessions média ===
        val diagButton = Button(this)
        diagButton.text = "Voir les sessions média actives"
        diagButton.setOnClickListener {
            try {
                val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                val component = ComponentName(this, NotificationListener::class.java)
                val sessions = manager.getActiveSessions(component)
                val sb = StringBuilder()
                sessions.forEachIndexed { i, c ->
                    sb.append("${i + 1}. ${c.packageName} (état ${c.playbackState?.state})\n")
                }
                diagText.text = if (sb.isEmpty()) "Aucune session active" else sb.toString()
            } catch (e: Exception) {
                diagText.text = "Erreur : " + e.message
            }
        }
        layout.addView(diagButton)
        layout.addView(diagText)

        val colorLabel = TextView(this)
        colorLabel.setPadding(0, 60, 0, 20)
        colorLabel.text = "Choisis ta couleur :"
        colorLabel.textSize = 16f
        layout.addView(colorLabel)

        val colors = listOf(
            Triple("Rose / Violet", "#FF3DBB", "#8A2BE2"),
            Triple("Bleu", "#00C6FF", "#0072FF"),
            Triple("Vert", "#00F260", "#0575E6"),
            Triple("Orange", "#FF8C00", "#FF3D00"),
            Triple("Rouge", "#FF416C", "#FF4B2B"),
            Triple("Jaune", "#FFE259", "#FFA751"),
            Triple("Turquoise", "#00FFA3", "#00C2FF"),
            Triple("Violet foncé", "#7F00FF", "#E100FF"),
            Triple("Corail", "#FF9966", "#FF5E62"),
            Triple("Bleu nuit", "#0F2027", "#2C5364"),
            Triple("Rose pastel", "#FFAFBD", "#FFC3A0"),
            Triple("Vert lime", "#A8E063", "#56AB2F"),
            Triple("Or", "#FFD700", "#FFA500"),
            Triple("Cyan", "#00FFFF", "#0080FF"),
            Triple("Magenta", "#FF00CC", "#333399"),
            Triple("Bronze", "#C09B6D", "#8B5E3C"),
            Triple("Menthe", "#00B09B", "#96C93D"),
            Triple("Lavande", "#C471ED", "#F64F59"),
            Triple("Sunset", "#FF512F", "#F09819"),
            Triple("Noir / Argent", "#434343", "#B0B0B0")
        )

        for ((name, c1, c2) in colors) {
            val colorButton = Button(this)
            colorButton.text = name
            colorButton.setBackgroundColor(Color.parseColor(c1))
            colorButton.setOnClickListener {
                prefs.edit()
                    .putString("color1", c1)
                    .putString("color2", c2)
                    .apply()
                colorLabel.text = "Choisis ta couleur : (actuel : $name)"
            }
            layout.addView(colorButton)
        }

        val crashFile = File(getExternalFilesDir(null), "crash_log.txt")
        val crashText = TextView(this)
        crashText.setPadding(0, 60, 0, 0)
        crashText.textSize = 12f
        if (crashFile.exists()) {
            crashText.text = "DERNIER CRASH :\n\n" + crashFile.readText()
        } else {
            crashText.text = "Aucun crash enregistré pour le moment."
        }
        layout.addView(crashText)

        val clearButton = Button(this)
        clearButton.text = "Effacer le journal de crash"
        clearButton.setOnClickListener {
            crashFile.delete()
            recreate()
        }
        layout.addView(clearButton)

        // === DIAG : dernières morts du processus ===
        val exitText = TextView(this)
        exitText.setPadding(0, 60, 0, 0)
        exitText.textSize = 12f
        exitText.text = "DERNIÈRES MORTS DU PROCESSUS :\n\n" + exitInfo
        layout.addView(exitText)

        scroll.addView(layout)
        setContentView(scroll)
    }
}
