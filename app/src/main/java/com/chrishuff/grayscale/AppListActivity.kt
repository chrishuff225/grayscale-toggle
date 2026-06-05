package com.chrishuff.grayscale

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
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
            val delayed = Prefs.getDelayed(this@AppListActivity).toMutableSet()
            b.recycler.adapter = AppAdapter(apps, excluded, delayed)
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

    /** Asks the launcher to pin an "open in color" shortcut for the given app. */
    private fun addColorShortcut(item: AppEntry) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "Your launcher doesn't support adding shortcuts", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OpenInColorActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(OpenInColorActivity.EXTRA_PACKAGE, item.pkg)
        }
        val shortcut = ShortcutInfoCompat.Builder(this, "color_${item.pkg}")
            .setShortLabel("${item.label} (color)")
            .setLongLabel("Open ${item.label} in color")
            .setIcon(IconCompat.createWithBitmap(item.icon.toBitmap(192, 192)))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    private inner class AppAdapter(
        private val items: List<AppEntry>,
        private val excluded: MutableSet<String>,
        private val delayed: MutableSet<String>
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
            holder.v.delayCheck.setOnCheckedChangeListener(null)

            val isExcluded = excluded.contains(item.pkg)
            holder.v.checkbox.isChecked = isExcluded
            holder.v.delayCheck.isChecked = delayed.contains(item.pkg)
            setExtrasVisible(holder, isExcluded)

            holder.v.root.setOnClickListener { holder.v.checkbox.toggle() }
            holder.v.btnAddShortcut.setOnClickListener { addColorShortcut(item) }

            holder.v.checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) excluded.add(item.pkg) else excluded.remove(item.pkg)
                Prefs.setExcludedFor(this@AppListActivity, item.pkg, checked)
                setExtrasVisible(holder, checked)
            }
            holder.v.delayCheck.setOnCheckedChangeListener { _, checked ->
                if (checked) delayed.add(item.pkg) else delayed.remove(item.pkg)
                Prefs.setDelayedFor(this@AppListActivity, item.pkg, checked)
            }
        }

        private fun setExtrasVisible(holder: VH, visible: Boolean) {
            val v = if (visible) View.VISIBLE else View.GONE
            holder.v.delayCheck.visibility = v
            holder.v.btnAddShortcut.visibility = v
        }
    }
}
