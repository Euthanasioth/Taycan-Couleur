package com.example.taycancolorproxy

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs: SharedPreferences = getSharedPreferences("taycan_couleur", MODE_PRIVATE)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 100, 40, 40)

        val text = TextView(this)
        text.text = "Taycan Couleur\n\nÉtape 1 : autorise l'accès aux notifications ci-dessous.\nÉtape 2 : choisis une couleur.\nÉtape 3 : lance Deezer et sélectionne \"Taycan Couleur\" dans Android Auto."
        text.textSize = 16f
        layout.addView(text)

        val button = Button(this)
        button.text = "Ouvrir les réglages d'accès aux notifications"
        button.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        layout.addView(button)

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

        scroll.addView(layout)
        setContentView(scroll)
    }
}
