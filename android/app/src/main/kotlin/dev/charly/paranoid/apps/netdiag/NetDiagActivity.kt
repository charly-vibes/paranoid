package dev.charly.paranoid.apps.netdiag

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netdiag.collect.SnapshotCaptureEngine
import dev.charly.paranoid.apps.netdiag.exchange.SnapshotFileExchange
import dev.charly.paranoid.apps.netdiag.data.ComparisonEngine
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsComparison
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsComparisonEntity
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSessionEntity
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshot
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSnapshotEntity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

class NetDiagActivity : AppCompatActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var db: ParanoidDatabase
    private var capturedSnapshot: DiagnosticsSnapshot? = null
    private var capturedSessionId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionState() }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importSnapshotFromUri(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_netdiag)

        db = ParanoidDatabase.getInstance(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_history).setOnClickListener {
            startActivity(Intent(this, SessionHistoryActivity::class.java))
        }

        findViewById<TextView>(R.id.btn_capture).setOnClickListener { startCapture() }
        findViewById<TextView>(R.id.btn_view_details).setOnClickListener { viewDetails() }
        findViewById<TextView>(R.id.btn_compare).setOnClickListener { compareWithSaved() }
        findViewById<TextView>(R.id.btn_share).setOnClickListener { shareSnapshot() }

        requestPermissions()
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            updatePermissionState()
        }
    }

    private fun updatePermissionState() {
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val btn = findViewById<TextView>(R.id.btn_capture)
        btn.isEnabled = hasLocation
        btn.alpha = if (hasLocation) 1f else 0.5f

        val hint = findViewById<TextView>(R.id.permission_hint)
        if (hasLocation) {
            hint.visibility = View.GONE
        } else {
            hint.visibility = View.VISIBLE
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                hint.text = "Location permission denied. Tap to open Settings."
                hint.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startCapture() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm.activeNetwork == null) {
            showError("No network connection. Connect to Wi-Fi or cellular and try again.")
            return
        }

        val btn = findViewById<TextView>(R.id.btn_capture)
        btn.isEnabled = false
        btn.text = "Capturing…"
        btn.alpha = 0.5f

        findViewById<View>(R.id.progress_section).visibility = View.VISIBLE
        findViewById<View>(R.id.error_text).visibility = View.GONE
        findViewById<View>(R.id.result_card).visibility = View.GONE
        findViewById<View>(R.id.action_buttons).visibility = View.GONE

        val sessionId = UUID.randomUUID().toString()
        val deviceLabel = Build.MODEL

        lifecycleScope.launch {
            val session = DiagnosticsSessionEntity(
                id = sessionId,
                label = deviceLabel,
                createdAtMs = System.currentTimeMillis(),
                notes = null,
            )
            withContext(Dispatchers.IO) { db.sessionDao().insert(session) }

            val engine = SnapshotCaptureEngine(this@NetDiagActivity)
            val result = engine.capture(deviceLabel, sessionId)

            findViewById<View>(R.id.progress_section).visibility = View.GONE

            when (result) {
                is SnapshotCaptureEngine.CaptureResult.Success -> {
                    val snapshot = result.snapshot
                    val entity = DiagnosticsSnapshotEntity(
                        id = snapshot.id,
                        sessionId = sessionId,
                        capturedAtMs = snapshot.capturedAtMs,
                        deviceLabel = snapshot.deviceLabel,
                        deviceModel = snapshot.deviceModel,
                        snapshotJson = json.encodeToString(DiagnosticsSnapshot.serializer(), snapshot),
                    )
                    withContext(Dispatchers.IO) { db.snapshotDao().insert(entity) }

                    capturedSnapshot = snapshot
                    capturedSessionId = sessionId
                    showResult(snapshot)

                    if (result.transportChanged) {
                        Toast.makeText(
                            this@NetDiagActivity,
                            "⚠ Network changed during capture. Results may be inconsistent.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                is SnapshotCaptureEngine.CaptureResult.Error -> {
                    showError(result.message)
                }
            }

            btn.text = "Run Diagnostics"
            btn.isEnabled = true
            btn.alpha = 1f
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showResult(snapshot: DiagnosticsSnapshot) {
        findViewById<View>(R.id.result_card).visibility = View.VISIBLE
        findViewById<View>(R.id.action_buttons).visibility = View.VISIBLE

        findViewById<TextView>(R.id.result_device).text =
            "${snapshot.deviceLabel} (Android ${snapshot.androidVersion})"

        val flags = mutableListOf<String>()
        if (snapshot.isValidated) flags.add("✓ Internet") else flags.add("✗ No internet")
        if (snapshot.isCaptivePortal) flags.add("Captive portal")
        if (snapshot.isVpnActive) flags.add("VPN active")
        if (snapshot.isDozed) flags.add("Doze mode")

        findViewById<TextView>(R.id.result_transport).text =
            "Transport: ${snapshot.activeTransport.name}"
        findViewById<TextView>(R.id.result_status).text = flags.joinToString(" · ")
    }

    private fun showError(message: String) {
        findViewById<TextView>(R.id.error_text).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun shareSnapshot() {
        val snapshot = capturedSnapshot ?: return
        val intent = SnapshotFileExchange.createExportIntent(this, snapshot)
        startActivity(Intent.createChooser(intent, "Share snapshot"))
    }

    private fun viewDetails() {
        val snapshot = capturedSnapshot ?: return
        startActivity(Intent(this, SnapshotDetailActivity::class.java).apply {
            putExtra("snapshot_id", snapshot.id)
        })
    }

    @SuppressLint("SetTextI18n")
    private fun compareWithSaved() {
        val current = capturedSnapshot ?: return

        lifecycleScope.launch {
            val allSnapshots = withContext(Dispatchers.IO) { db.snapshotDao().getAll() }
            val others = allSnapshots.filter { it.id != current.id }

            if (others.isEmpty()) {
                AlertDialog.Builder(this@NetDiagActivity)
                    .setTitle("No saved snapshots")
                    .setMessage("Capture a snapshot on another device first, or import one from a file.")
                    .setPositiveButton("Import from file") { _, _ ->
                        importFileLauncher.launch("application/json")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            val dateFmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US)
            val labels = others.map { "${it.deviceLabel} — ${dateFmt.format(java.util.Date(it.capturedAtMs))}" }
                .plus("Import from file…")

            AlertDialog.Builder(this@NetDiagActivity)
                .setTitle("Compare with")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which < others.size) {
                        runComparison(current, others[which])
                    } else {
                        importFileLauncher.launch("application/json")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun importSnapshotFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SnapshotFileExchange.importFromUri(this@NetDiagActivity, uri)
            }

            result.fold(
                onSuccess = { snapshot ->
                    val sessionId = capturedSessionId ?: UUID.randomUUID().toString()
                    val entity = DiagnosticsSnapshotEntity(
                        id = snapshot.id,
                        sessionId = sessionId,
                        capturedAtMs = snapshot.capturedAtMs,
                        deviceLabel = snapshot.deviceLabel,
                        deviceModel = snapshot.deviceModel,
                        snapshotJson = json.encodeToString(DiagnosticsSnapshot.serializer(), snapshot),
                    )
                    withContext(Dispatchers.IO) { db.snapshotDao().insert(entity) }

                    val current = capturedSnapshot
                    if (current != null) {
                        runComparison(current, entity)
                    } else {
                        Toast.makeText(this@NetDiagActivity, "Snapshot imported", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { error ->
                    showError(error.message ?: "Failed to import snapshot")
                },
            )
        }
    }

    private fun runComparison(current: DiagnosticsSnapshot, otherEntity: DiagnosticsSnapshotEntity) {
        lifecycleScope.launch {
            val other = try {
                json.decodeFromString<DiagnosticsSnapshot>(otherEntity.snapshotJson)
            } catch (e: Exception) {
                showError("Failed to load saved snapshot")
                return@launch
            }

            val comparison = ComparisonEngine.compare(current, other)

            val comparisonEntity = DiagnosticsComparisonEntity(
                id = comparison.id,
                sessionId = capturedSessionId ?: current.sessionId,
                comparedAtMs = comparison.comparedAtMs,
                overallStatus = comparison.summary.overallStatus.name,
                comparisonJson = json.encodeToString(DiagnosticsComparison.serializer(), comparison),
            )
            withContext(Dispatchers.IO) { db.comparisonDao().insert(comparisonEntity) }

            startActivity(Intent(this@NetDiagActivity, ComparisonResultActivity::class.java).apply {
                putExtra("comparison_id", comparison.id)
            })
        }
    }
}
