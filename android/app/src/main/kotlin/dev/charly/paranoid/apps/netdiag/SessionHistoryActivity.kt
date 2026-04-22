package dev.charly.paranoid.apps.netdiag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsComparisonEntity
import dev.charly.paranoid.apps.netdiag.data.DiagnosticsSessionEntity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var db: ParanoidDatabase
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        db = ParanoidDatabase.getInstance(this)

        val list = findViewById<RecyclerView>(R.id.sessions_list)
        val empty = findViewById<TextView>(R.id.empty_state)

        adapter = SessionAdapter(
            onClick = { session ->
                lifecycleScope.launch {
                    val comparisons = db.comparisonDao().getBySessionId(session.id)
                    if (comparisons.isNotEmpty()) {
                        startActivity(Intent(this@SessionHistoryActivity, ComparisonResultActivity::class.java).apply {
                            putExtra("comparison_id", comparisons.first().id)
                        })
                    } else {
                        val snapshots = db.snapshotDao().getBySessionId(session.id)
                        if (snapshots.isNotEmpty()) {
                            startActivity(Intent(this@SessionHistoryActivity, SnapshotDetailActivity::class.java).apply {
                                putExtra("snapshot_id", snapshots.first().id)
                            })
                        }
                    }
                }
            },
            onLongClick = { session ->
                AlertDialog.Builder(this)
                    .setTitle("Delete session?")
                    .setMessage(session.label)
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            db.sessionDao().deleteById(session.id)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            loadComparison = { sessionId ->
                db.comparisonDao().getBySessionId(sessionId).firstOrNull()
            }
        )

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        lifecycleScope.launch {
            db.sessionDao().listByDateDesc().collect { sessions ->
                adapter.submitList(sessions)
                empty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

private class SessionAdapter(
    private val onClick: (DiagnosticsSessionEntity) -> Unit,
    private val onLongClick: (DiagnosticsSessionEntity) -> Unit,
    private val loadComparison: suspend (String) -> DiagnosticsComparisonEntity?,
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private var items: List<DiagnosticsSessionEntity> = emptyList()
    private val comparisonCache = mutableMapOf<String, DiagnosticsComparisonEntity?>()
    private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<DiagnosticsSessionEntity>) {
        items = list
        comparisonCache.clear()
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.session_label)
        val info: TextView = view.findViewById(R.id.session_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false))

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.label.text = s.label
        holder.info.text = dateFmt.format(Date(s.createdAtMs))

        if (comparisonCache.containsKey(s.id)) {
            holder.info.text = formatInfo(s, comparisonCache[s.id])
        } else {
            kotlinx.coroutines.MainScope().launch {
                val comparison = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    loadComparison(s.id)
                }
                comparisonCache[s.id] = comparison
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size && items[currentPos].id == s.id) {
                    holder.info.text = formatInfo(s, comparison)
                }
            }
        }

        holder.itemView.setOnClickListener { onClick(s) }
        holder.itemView.setOnLongClickListener { onLongClick(s); true }
    }

    private fun formatInfo(s: DiagnosticsSessionEntity, comparison: DiagnosticsComparisonEntity?): String {
        val date = dateFmt.format(Date(s.createdAtMs))
        return if (comparison != null) "$date · ${comparison.overallStatus}" else date
    }

    override fun getItemCount() = items.size
}
