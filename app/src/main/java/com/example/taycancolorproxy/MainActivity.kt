package com.example.taycancolorproxy

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 100, 40, 40)

        val text = TextView(this)
        text.text = "Taycan Couleur\n\nÉtape 1 : autorise l'accès aux notifications ci-dessous.\nÉtape 2 : lance Deezer et joue un titre.\nÉtape 3 : choisis \"Taycan Couleur\" comme source média dans Android Auto."
        text.textSize = 16f
        layout.addView(text)

        val button = Button(this)
        button.text = "Ouvrir les réglages d'accès aux notifications"
        button.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        layout.addView(button)

        setContentView(layout)
    }
}
