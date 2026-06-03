package com.chrishuff.grayscale

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrishuff.grayscale.databinding.ActivityAppListBinding
import com.chrishuff.grayscale.databinding.ItemAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lets the user choose which apps should stay in color (never grayscale). */
class AppListActivity : AppCompatActivity() {

    private lateinit var b: ActivityAppListBinding

    data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.excluded_title)

        b.recycler.layoutManager = LinearLayoutManager(this)
        loadApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadApps() {
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { queryApps() }
            val excluded = Prefs.getExcluded(this@AppListActivity).toMutableSet()
            b.recycler.adapter = AppAdapter(apps, excluded)
            b.progress.visibility = View.GONE
        }
    }

    private fun queryApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val list = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            list.add(AppEntry(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)))
        }
        list.sortBy { it.label.lowercase() }
        return list
    }

    private inner class AppAdapter(
        private val items: List<AppEntry>,
        private val excluded: MutableSet<String>
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(val v: ItemAppBinding) : RecyclerView.ViewHolder(v.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.v.icon.setImageDrawable(item.icon)
            holder.v.label.text = item.label
            holder.v.checkbox.setOnCheckedChangeListener(null)
            holder.v.checkbox.isChecked = excluded.contains(item.pkg)
            holder.v.root.setOnClickListener { holder.v.checkbox.toggle() }
            holder.v.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) excluded.add(item.pkg) else excluded.remove(item.pkg)
                Prefs.setExcludedFor(this@AppListActivity, item.pkg, isChecked)
            }
        }
    }
}
