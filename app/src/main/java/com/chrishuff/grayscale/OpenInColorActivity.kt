package com.chrishuff.grayscale

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible activity behind a home-screen "open in color" shortcut. It turns grayscale
 * off (a short global pause) and then launches the target app, so the app comes to the
 * foreground with grayscale already off — avoiding the mid-foreground toggle that closes
 * apps like Focus Friend. Once the (excluded) app is foreground it stays in color on its
 * own; the pause just covers the launch transition.
 */
class OpenInColorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE)
        if (pkg.isNullOrEmpty()) {
            finish()
            return
        }

        TimerReceiver.startPause(this, PAUSE_MS)

        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        private const val PAUSE_MS = 8000L
    }
}
