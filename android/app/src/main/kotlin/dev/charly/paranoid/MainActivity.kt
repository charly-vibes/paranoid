package dev.charly.paranoid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appsList = findViewById<RecyclerView>(R.id.apps_list)
        val emptyState = findViewById<TextView>(R.id.empty_state)
        val infoButton = findViewById<TextView>(R.id.info_button)

        val apps = AppRegistry.apps

        if (apps.isEmpty()) {
            appsList.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            appsList.layoutManager = LinearLayoutManager(this)
            appsList.adapter = AppListAdapter(apps) { app ->
                startActivity(Intent(this, app.activityClass))
            }
        }

        infoButton.setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }
    }
}
