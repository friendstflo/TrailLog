package org.mountaineers.traillog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "TEST ACTIVITY\n\nIf you see this, the problem is in MainActivity or navigation setup."
            textSize = 20f
            setPadding(100, 200, 100, 200)
            gravity = android.view.Gravity.CENTER
        }

        setContentView(tv)
    }
}