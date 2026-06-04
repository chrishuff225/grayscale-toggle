package com.chrishuff.grayscale

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chrishuff.grayscale.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        b.btnCopyCommand.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", adbCommand()))
            Toast.makeText(this, "Command copied", Toast.LENGTH_SHORT).show()
        }

        b.btnEnableAccessibility.setOnClickListener { openAccessibilitySettings() }

        b.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }

        b.btnStartPause.setOnClickListener { startPause() }

        b.btnCancelPause.setOnClickListener {
            TimerReceiver.cancelPause(this)
            updateUi()
        }
    }

    private fun adbCommand(): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

    /** Opens the system accessibility settings, deep-linked to Smart Gray's own
     *  service entry where possible, falling back to the general list. */
    private fun openAccessibilitySettings() {
        val component = ComponentName(this, GrayscaleAccessibilityService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val args = Bundle().apply { putString(":settings:fragment_args_key", component) }
        intent.putExtra(":settings:fragment_args_key", component)
        intent.putExtra(":settings:show_fragment_args", args)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't open accessibility settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPause() {
        val minutes = b.editMinutes.text.toString().toLongOrNull()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(this, "Enter a number of minutes greater than 0", Toast.LENGTH_SHORT).show()
            return
        }
        TimerReceiver.startPause(this, minutes * 60_000L)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        countdown?.cancel()
        countdown = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, AppListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateUi() {
        // One-time setup cards.
        b.cardPermission.visibility =
            if (GrayscaleManager.hasPermission(this)) View.GONE else View.VISIBLE
        b.textAdbCommand.text = adbCommand()
        // Accessibility service powers the per-app "keep in color" feature. Show the
        // card unless the service is both enabled AND actually running.
        val serviceListed = isAccessibilityEnabled()
        val serviceRunning = GrayscaleAccessibilityService.isRunning
        if (serviceListed && serviceRunning) {
            b.cardAccessibility.visibility = View.GONE
        } else {
            b.cardAccessibility.visibility = View.VISIBLE
            b.textAccessibilityDesc.text = if (!serviceListed) {
                "Required for \"apps to keep in color\". Turn on Smart Gray under " +
                    "Accessibility. If the switch is greyed out — common for apps installed " +
                    "outside the Play Store — first open App info and choose \"Allow " +
                    "restricted settings\", then come back and enable it."
            } else {
                "The service is switched on but isn't running yet. Toggle Smart Gray " +
                    "off and back on in Accessibility settings."
            }
        }

        // Master switch (detach listener while setting the value programmatically).
        b.switchMaster.setOnCheckedChangeListener(null)
        b.switchMaster.isChecked = Prefs.isMasterEnabled(this)
        b.switchMaster.setOnCheckedChangeListener { _, checked ->
            Prefs.setMasterEnabled(this, checked)
            GrayscaleManager.applyEffectiveState(this, null)
            updateUi()
        }

        // Pause timer state + countdown display.
        val pausedUntil = Prefs.getPausedUntil(this)
        val remaining = pausedUntil - System.currentTimeMillis()
        countdown?.cancel()
        countdown = null
        if (remaining > 0) {
            b.textTimerStatus.visibility = View.VISIBLE
            b.btnCancelPause.visibility = View.VISIBLE
            countdown = object : CountDownTimer(remaining, 1000) {
                override fun onTick(ms: Long) {
                    b.textTimerStatus.text =
                        "Paused — grayscale returns in ${DateUtils.formatElapsedTime(ms / 1000)}"
                }

                override fun onFinish() {
                    updateUi()
                }
            }.start()
        } else {
            b.textTimerStatus.visibility = View.GONE
            b.btnCancelPause.visibility = View.GONE
        }

        val screen = if (GrayscaleManager.isGrayscaleOn(this)) "GRAYSCALE" else "COLOR"
        val exclusions = if (serviceRunning) "active" else "inactive — enable the service above"
        b.textStatus.text = "Screen is currently $screen\nApp exclusions: $exclusions"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val full = "$packageName/${GrayscaleAccessibilityService::class.java.name}"
        val short = "$packageName/.GrayscaleAccessibilityService"
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }
}
