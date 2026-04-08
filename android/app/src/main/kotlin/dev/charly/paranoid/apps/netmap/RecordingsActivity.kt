package dev.charly.paranoid.apps.netmap

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
import dev.charly.paranoid.apps.netmap.data.MeasurementEntity
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.netmap.data.RecordingEntity
import kotlinx.coroutines.launch

class RecordingsActivity : AppCompatActivity() {

    private lateinit var db: ParanoidDatabase
    private lateinit var adapter: RecordingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)

        db = ParanoidDatabase.getInstance(this)

        val list = findViewById<RecyclerView>(R.id.recordings_list)
        val empty = findViewById<TextView>(R.id.empty_state)

        adapter = RecordingAdapter(
            onClick = { recording ->
                startActivity(Intent(this, RecordingDetailActivity::class.java).apply {
                    putExtra("recording_id", recording.id)
                })
            },
            onLongClick = { recording ->
                AlertDialog.Builder(this)
                    .setTitle("Delete recording?")
                    .setMessage(recording.name)
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            db.recordingDao().deleteById(recording.id)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        lifecycleScope.launch {
            db.recordingDao().observeAll().collect { recordings ->
                adapter.submitList(recordings)
                empty.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (recordings.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

private class RecordingAdapter(
    private val onClick: (RecordingEntity) -> Unit,
    private val onLongClick: (RecordingEntity) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.VH>() {

    private var items: List<RecordingEntity> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<RecordingEntity>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.recording_name)
        val stats: TextView = view.findViewById(R.id.recording_stats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false))

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.name.text = r.name

        val duration = if (r.endedAt != null) {
            val secs = (r.endedAt - r.startedAt) / 1000
            val m = secs / 60
            val s = secs % 60
            "${m}m ${s}s"
        } else "In progress"

        holder.stats.text = duration
        holder.itemView.setOnClickListener { onClick(r) }
        holder.itemView.setOnLongClickListener { onLongClick(r); true }
    }

    override fun getItemCount() = items.size
}
