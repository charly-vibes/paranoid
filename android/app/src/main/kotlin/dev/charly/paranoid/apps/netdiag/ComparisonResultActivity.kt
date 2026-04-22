package dev.charly.paranoid.apps.netdiag

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netdiag.data.AffectedDevice
import dev.charly.paranoid.apps.netdiag.data.ComparisonFinding
import dev.charly.paranoid.apps.netdiag.data.ComparisonStatus
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsComparison
import dev.charly.paranoid.apps.netdiag.data.Severity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ComparisonResultActivity : AppCompatActivity() {

    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparison_result)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        val comparisonId = intent.getStringExtra("comparison_id") ?: run {
            showError("No comparison ID provided")
            return
        }

        val db = ParanoidDatabase.getInstance(this)

        lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) {
                db.comparisonDao().getById(comparisonId)
            }

            if (entity == null) {
                showError("Comparison not found")
                return@launch
            }

            val comparison = try {
                json.decodeFromString<DiagnosticsComparison>(entity.comparisonJson)
            } catch (e: Exception) {
                showError("Failed to load comparison data")
                return@launch
            }

            findViewById<View>(R.id.loading_text).visibility = View.GONE
            populateSummary(comparison)
            populateFindings(comparison.findings)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateSummary(comparison: DiagnosticsComparison) {
        val summaryCard = findViewById<LinearLayout>(R.id.summary_card)
        summaryCard.visibility = View.VISIBLE

        val summary = comparison.summary

        // Status badge
        val badge = findViewById<TextView>(R.id.status_badge)
        val (statusText, statusColor) = when (summary.overallStatus) {
            ComparisonStatus.IDENTICAL -> "IDENTICAL" to Color.parseColor("#4CAF50")
            ComparisonStatus.MINOR_DIFF -> "MINOR DIFFERENCE" to Color.parseColor("#64B5F6")
            ComparisonStatus.ONE_DEGRADED -> "ONE DEGRADED" to Color.parseColor("#FFB74D")
            ComparisonStatus.BOTH_DEGRADED -> "BOTH DEGRADED" to Color.parseColor("#FF5252")
            ComparisonStatus.INCOMPARABLE -> "INCOMPARABLE" to Color.parseColor("#FF5252")
        }
        badge.text = statusText
        badge.background = GradientDrawable().apply {
            setColor(statusColor)
            cornerRadius = dpToPx(4f)
        }

        // Device labels
        val snapA = comparison.snapshotA
        val snapB = comparison.snapshotB
        findViewById<TextView>(R.id.device_a_label).text = "A: ${snapA.deviceLabel}"
        findViewById<TextView>(R.id.device_b_label).text = "B: ${snapB.deviceLabel}"

        // Finding counts
        val counts = buildString {
            if (summary.criticalCount > 0) append("🔴 ${summary.criticalCount} critical  ")
            if (summary.warningCount > 0) append("🟠 ${summary.warningCount} warning  ")
            if (summary.infoCount > 0) append("🔵 ${summary.infoCount} info")
        }.trim()
        findViewById<TextView>(R.id.finding_counts).text =
            counts.ifEmpty { "No findings" }

        // Likely cause
        val causeView = findViewById<TextView>(R.id.likely_cause)
        if (summary.likelyCause != null) {
            causeView.text = "Likely cause: ${summary.likelyCause}"
            causeView.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateFindings(findings: List<ComparisonFinding>) {
        if (findings.isEmpty()) return

        findViewById<TextView>(R.id.findings_header).visibility = View.VISIBLE
        val container = findViewById<LinearLayout>(R.id.findings_container)

        for (finding in findings) {
            val card = buildFindingCard(finding)
            container.addView(card)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildFindingCard(finding: ComparisonFinding): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.app_item_bg, theme)
            setPadding(dpToPxInt(16), dpToPxInt(12), dpToPxInt(16), dpToPxInt(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dpToPxInt(8) }
            isClickable = true
            isFocusable = true
        }

        // Row 1: severity dot + metric + affected device
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        // Severity dot
        val dot = View(this).apply {
            val size = dpToPxInt(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPxInt(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(severityColor(finding.severity))
            }
        }
        headerRow.addView(dot)

        // Metric name
        val metricText = TextView(this).apply {
            text = finding.metric
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(metricText)

        // Affected device badge
        if (finding.affectedDevice != AffectedDevice.NEITHER) {
            val deviceBadge = TextView(this).apply {
                text = when (finding.affectedDevice) {
                    AffectedDevice.A -> "Device A"
                    AffectedDevice.B -> "Device B"
                    AffectedDevice.BOTH -> "Both"
                    AffectedDevice.NEITHER -> ""
                }
                setTextColor(Color.parseColor("#888888"))
                textSize = 11f
                setPadding(dpToPxInt(6), dpToPxInt(2), dpToPxInt(6), dpToPxInt(2))
                background = GradientDrawable().apply {
                    setStroke(1, Color.parseColor("#555555"))
                    cornerRadius = dpToPx(4f)
                }
            }
            headerRow.addView(deviceBadge)
        }

        card.addView(headerRow)

        // Row 2: values A vs B + delta
        val valuesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dpToPxInt(6) }
        }

        val valA = TextView(this).apply {
            text = "A: ${finding.valueA}"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dpToPxInt(16) }
        }
        valuesRow.addView(valA)

        val valB = TextView(this).apply {
            text = "B: ${finding.valueB}"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        valuesRow.addView(valB)

        if (finding.delta != null) {
            val deltaText = TextView(this).apply {
                val pct = finding.deltaPercent?.let { " (%.0f%%)".format(it) } ?: ""
                text = "Δ ${finding.delta}$pct"
                setTextColor(severityColor(finding.severity))
                textSize = 12f
            }
            valuesRow.addView(deltaText)
        }

        card.addView(valuesRow)

        // Expandable: explanation + recommendation (hidden by default)
        val expandable = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dpToPxInt(8) }
        }

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPxInt(1),
            ).apply { bottomMargin = dpToPxInt(8) }
            setBackgroundColor(Color.parseColor("#333333"))
        }
        expandable.addView(divider)

        val explanationLabel = TextView(this).apply {
            text = "Explanation"
            setTextColor(Color.parseColor("#888888"))
            textSize = 11f
        }
        expandable.addView(explanationLabel)

        val explanationText = TextView(this).apply {
            text = finding.explanation
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dpToPxInt(8) }
        }
        expandable.addView(explanationText)

        val recLabel = TextView(this).apply {
            text = "Recommendation"
            setTextColor(Color.parseColor("#888888"))
            textSize = 11f
        }
        expandable.addView(recLabel)

        val recText = TextView(this).apply {
            text = finding.recommendation
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
        }
        expandable.addView(recText)

        card.addView(expandable)

        // Toggle on tap
        card.setOnClickListener {
            expandable.visibility =
                if (expandable.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        return card
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.loading_text).visibility = View.GONE
        findViewById<TextView>(R.id.error_text).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun severityColor(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> Color.parseColor("#FF5252")
        Severity.WARNING -> Color.parseColor("#FFB74D")
        Severity.INFO -> Color.parseColor("#64B5F6")
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun dpToPxInt(dp: Int): Int = dpToPx(dp.toFloat()).toInt()
}
