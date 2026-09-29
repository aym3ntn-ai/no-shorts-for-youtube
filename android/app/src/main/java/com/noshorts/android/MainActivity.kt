package com.noshorts.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.noshorts.android.databinding.ActivityMainBinding

/** Settings and status screen. The blocking itself lives in [ShortsBlockerService]. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.openAccessibility.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            settings.enabled = checked
            refreshStatus()
        }
        binding.switchToast.setOnCheckedChangeListener { _, checked -> settings.showToast = checked }
        binding.switchAggressive.setOnCheckedChangeListener { _, checked -> settings.aggressive = checked }
        binding.switchTitles.setOnCheckedChangeListener { _, checked ->
            settings.titleBlockingEnabled = checked
        }
        binding.switchLinkTrap.setOnCheckedChangeListener { _, checked ->
            settings.linkTrapEnabled = checked
        }

        binding.copyIds.setOnClickListener { copyObservedIds() }
        binding.clearIds.setOnClickListener {
            settings.clearObservedIds()
            refreshDiagnostics()
        }
        binding.resetStats.setOnClickListener {
            settings.blockedCount = 0
            refreshStats()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchToast.isChecked = settings.showToast
        binding.switchAggressive.isChecked = settings.aggressive
        binding.switchTitles.isChecked = settings.titleBlockingEnabled
        binding.switchLinkTrap.isChecked = settings.linkTrapEnabled
        binding.blockedTitles.setText(settings.blockedTitles.joinToString("\n"))
        binding.extraMarkers.setText(settings.extraMarkers.joinToString("\n"))
        refreshStatus()
        refreshStats()
        refreshDiagnostics()
    }

    override fun onPause() {
        // Text fields save on the way out; the service picks the change up
        // through the shared-preferences listener.
        settings.blockedTitles = binding.blockedTitles.text.toString().split('\n')
            .map { it.trim() }.filter { it.isNotEmpty() }
        settings.extraMarkers = binding.extraMarkers.text.toString().split('\n')
            .map { it.trim() }.filter { it.isNotEmpty() }
        super.onPause()
    }

    private fun refreshStatus() {
        val serviceOn = Settings.isAccessibilityServiceEnabled(this)
        val blocking = serviceOn && settings.enabled

        binding.statusTitle.setText(
            when {
                blocking -> R.string.status_active
                serviceOn -> R.string.status_paused
                else -> R.string.status_service_off
            }
        )

        val detail = when {
            blocking -> getString(R.string.status_active_detail)
            serviceOn -> getString(R.string.status_paused_detail)
            else -> getString(R.string.status_service_off_detail)
        }
        binding.statusDetail.text = if (isYouTubeInstalled()) {
            detail
        } else {
            detail + "\n\n" + getString(R.string.status_no_youtube)
        }

        binding.openAccessibility.visibility = if (serviceOn) View.GONE else View.VISIBLE
    }

    private fun refreshStats() {
        val count = settings.blockedCount
        binding.stats.text = resources.getQuantityString(R.plurals.stats_format, count, count)
    }

    private fun refreshDiagnostics() {
        val ids = settings.observedIds
        binding.observedIds.text = if (ids.isEmpty()) {
            getString(R.string.observed_empty)
        } else {
            ids.joinToString("\n")
        }
        binding.copyIds.isEnabled = ids.isNotEmpty()
    }

    private fun copyObservedIds() {
        val ids = settings.observedIds
        if (ids.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("shorts view ids", ids.joinToString("\n")))
        Toast.makeText(this, R.string.ids_copied, Toast.LENGTH_SHORT).show()
    }

    private fun isYouTubeInstalled() = ShortsBlockerService.YOUTUBE_PACKAGES.any { pkg ->
        runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }
}
