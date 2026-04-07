package dev.charly.paranoid

import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        val buildSection = findViewById<LinearLayout>(R.id.build_section)
        val envSection = findViewById<LinearLayout>(R.id.env_section)

        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        addRow(buildSection, "Version", packageInfo.versionName ?: "unknown")
        addRow(buildSection, "Version Code", packageInfo.longVersionCode.toString())
        addRow(buildSection, "Package", packageName)
        addRow(buildSection, "Mini-apps", AppRegistry.apps.size.toString())

        addRow(envSection, "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        addRow(envSection, "Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        addRow(envSection, "Build", Build.DISPLAY)
        addRow(envSection, "ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
    }

    private fun addRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(0xFF888888.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlignment = TextView.TEXT_ALIGNMENT_TEXT_END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(labelView)
        row.addView(valueView)
        parent.addView(row)
    }
}
