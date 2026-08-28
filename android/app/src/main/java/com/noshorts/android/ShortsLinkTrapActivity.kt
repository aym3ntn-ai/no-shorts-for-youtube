package com.noshorts.android

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Registers for youtube.com/shorts/... links and throws them away.
 *
 * Disabled by default; the settings screen flips the component on. Android
 * hands verified links straight to the YouTube app, so this only catches links
 * that reach the chooser — it's a complement to the accessibility service, not
 * a replacement for it.
 */
class ShortsLinkTrapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, R.string.toast_link_discarded, Toast.LENGTH_SHORT).show()
        finish()
    }
}
