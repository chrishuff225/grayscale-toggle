package com.chrishuff.grayscale

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

        b.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.btnStartPause.setOnClickListener { startPause() }

        b.btnCancelPause.setOnClickListener {
            TimerReceiver.cancelPause(this)
            updateUi()
        }
    }

    private fun adbCommand(): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

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
        b.cardAccessibility.visibility =
            if (isAccessibilityEnabled()) View.GONE else View.VISIBLE

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

        b.textStatus.text =
            "Screen is currently " + if (GrayscaleManager.isGrayscaleOn(this)) "GRAYSCALE" else "COLOR"
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
