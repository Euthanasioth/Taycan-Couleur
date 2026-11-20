
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

        Logger.init(applicationContext)

        // Enregistre la trace exacte d'un plantage
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

        val prefs: SharedPreferences =
            getSharedPreferences("taycan_couleur", MODE_PRIVATE)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }

        val text = TextView(this).apply {
            text = "Taycan Couleur\n\nÉtape 1 : autorise l'accès aux notifications ci-dessous.\nÉtape 2 : démarre le proxy.\nÉtape 3 : choisis une couleur.\nÉtape 4 : lance Deezer et sélectionne \"Taycan Couleur\" dans Android Auto (ou une app proxy sur ton téléphone)."
            textSize = 16f
        }
        layout.addView(text)

        val button = Button(this).apply {
            text = "Ouvrir les réglages d'accès aux notifications"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }
        layout.addView(button)

        val diagText = TextView(this).apply {
            textSize = 12f
        }

        // Démarrer le proxy
        val startProxyBtn = Button(this).apply {
            text = "▶ Démarrer le proxy"
            setOnClickListener {
                try {
                    val intent = Intent(
                        this@MainActivity,
                        ColorProxyService::class.java
                    )
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
        }
        layout.addView(startProxyBtn)

        // Arrêter le proxy
        val stopProxyBtn = Button(this).apply {
            text = "⏹ Arrêter le proxy"
            setOnClickListener {
                try {
                    stopService(
                        Intent(this@MainActivity, ColorProxyService::class.java)
                    )
                    diagText.text = "Proxy arrêté"
                } catch (e: Exception) {
                    diagText.text = "Erreur arrêt : ${e.message}"
                }
            }
        }
        layout.addView(stopProxyBtn)

        // Diagnostic des sessions média
        val diagButton = Button(this).apply {
            text = "Voir les sessions média actives"
            setOnClickListener {
                try {
                    val manager =
                        getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                    val component = ComponentName(
                        this@MainActivity,
                        NotificationListener::class.java
                    )
                    val sessions = manager.getActiveSessions(component)

                    val sb = StringBuilder()
                    sessions.forEachIndexed { i, c ->
                        sb.append(
                            "${i + 1}. ${c.packageName} (état ${c.playbackState?.state})\n"
                        )
                    }

                    diagText.text = if (sb.isEmpty()) {
                        "Aucune session active"
                    } else {
                        sb.toString()
                    }
                } catch (e: Exception) {
                    diagText.text = "Erreur : ${e.message}"
                }
            }
        }
        layout.addView(diagButton)
        layout.addView(diagText)

        // Accès à la page secondaire
        val diagnosticsButton = Button(this).apply {
            text = "📋 Journal & diagnostics"
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, DiagnosticsActivity::class.java)
                )
            }
        }
        layout.addView(diagnosticsButton)

        // Choix des couleurs
        val colorLabel = TextView(this).apply {
            setPadding(0, 60, 0, 20)
            text = "Choisis ta couleur :"
            textSize = 16f
        }
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
            val colorButton = Button(this).apply {
                text = name
                setBackgroundColor(Color.parseColor(c1))
                setOnClickListener {
                    prefs.edit()
                        .putString("color1", c1)
                        .putString("color2", c2)
                        .apply()

                    colorLabel.text = "Choisis ta couleur : (actuel : $name)"
                }
            }
            layout.addView(colorButton)
        }

        scroll.addView(layout)
        setContentView(scroll)
    }
}
