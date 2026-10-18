package com.codeforge.android

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.content.pm.ActivityInfo

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(24, 24, 32))
        }

        val title = TextView(this).apply {
            text = "CodeForge"
            textSize = 32f
            setTextColor(Color.rgb(190, 150, 255))
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Android app initialized.\\n\\nBackend integration is the next step."
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(status)
        setContentView(root)
    }
}
