package dev.charly.paranoid.apps.sensorlogger

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.charly.paranoid.R
import dev.charly.paranoid.apps.sensorlogger.data.SensorSessionEntity
import dev.charly.paranoid.apps.sensorlogger.ui.SensorSessionsViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorSessionsActivity : AppCompatActivity() {

    private val viewModel: SensorSessionsViewModel by viewModels()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_sessions)

        val list = findViewById<RecyclerView>(R.id.sessions_list)
        val empty = findViewById<TextView>(R.id.empty_state)

        adapter = SessionAdapter { session ->
            startActivity(Intent(this, SensorSessionDetailActivity::class.java).apply {
                putExtra(SensorSessionDetailActivity.EXTRA_SESSION_ID, session.id)
            })
        }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        DebugLog.d("SensorSessionsActivity: onCreate, subscribing to sessions")
        lifecycleScope.launch {
            viewModel.sessions
                .catch { t ->
                    DebugLog.e("SensorSessionsActivity: sessions flow failed", t)
                    empty.text = "Could not load sessions: ${t.message}"
                    empty.visibility = View.VISIBLE
                    list.visibility = View.GONE
                }
                .collect { sessions ->
                    DebugLog.d("SensorSessionsActivity: received ${sessions.size} sessions")
                    adapter.submitList(sessions)
                    empty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    list.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
                }
        }
    }
}

private class SessionAdapter(
    private val onClick: (SensorSessionEntity) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private var items: List<SensorSessionEntity> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SensorSessionEntity>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val start: TextView = view.findViewById(R.id.tv_session_start)
        val duration: TextView = view.findViewById(R.id.tv_session_duration)
        val badge: TextView = view.findViewById(R.id.tv_incomplete_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sensor_session, parent, false))

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.start.text = dateFmt.format(Date(s.startedAt))
        if (s.endedAt == null) {
            holder.duration.visibility = View.GONE
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.duration.visibility = View.VISIBLE
            holder.badge.visibility = View.GONE
            val secs = (s.endedAt - s.startedAt) / 1000
            val m = secs / 60
            val ss = secs % 60
            holder.duration.text = "%d:%02d".format(m, ss)
        }
        holder.itemView.setOnClickListener { onClick(s) }
    }

    override fun getItemCount() = items.size
}
