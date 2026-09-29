package com.noshorts.android

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.noshorts.detector.BlockAction
import com.noshorts.detector.BlockController
import com.noshorts.detector.BlockReason
import com.noshorts.detector.DetectorConfig
import com.noshorts.detector.NodeSignature
import com.noshorts.detector.ScreenAnalyzer
import com.noshorts.detector.Verdict

/**
 * Watches the YouTube app's accessibility tree and backs out of the Shorts
 * player the moment it appears.
 *
 * This is the only mechanism available on an unrooted phone: the native app
 * can't be scripted like a web page, so instead we read the on-screen view
 * hierarchy YouTube already publishes for screen readers, and press Back when
 * it says we're in the Shorts player. All decision-making lives in the
 * `:detector` module so it can be unit-tested off-device.
 */
class ShortsBlockerService : AccessibilityService() {

    private lateinit var settings: Settings
    private val controller = BlockController()

    @Volatile
    private var config: DetectorConfig = DetectorConfig()

    @Volatile
    private var enabled: Boolean = true

    private var lastScanAt = 0L

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadSettings()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings(this)
        reloadSettings()
        settings.registerListener(prefsListener)
        Log.i(TAG, "Shorts blocker connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (this::settings.isInitialized) settings.unregisterListener(prefsListener)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!enabled || event == null) return
        if (event.packageName?.toString() !in YOUTUBE_PACKAGES) return

        // YouTube emits content-changed events continuously while a video
        // plays; scanning every one of them would be a battery bug.
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAt < MIN_SCAN_INTERVAL_MS) return
        lastScanAt = now

        val root = rootInActiveWindow ?: return
        val analyzer = ScreenAnalyzer(config)
        scan(root, analyzer)
        val analysis = analyzer.analysis

        settings.recordObservedIds(analysis.observedShortsIds)

        when (controller.onVerdict(analysis.verdict, now)) {
            BlockAction.NONE -> Unit
            BlockAction.BACK -> act(analysis.verdict, GLOBAL_ACTION_BACK)
            BlockAction.HOME -> act(analysis.verdict, GLOBAL_ACTION_HOME)
        }
    }

    private fun act(verdict: Verdict, globalAction: Int) {
        val blocked = verdict as? Verdict.Block ?: return
        performGlobalAction(globalAction)
        settings.recordBlock()
        Log.i(TAG, "Blocked ${blocked.reason} (${blocked.evidence})")
        if (settings.showToast) {
            Toast.makeText(this, toastFor(blocked.reason), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toastFor(reason: BlockReason) = when (reason) {
        BlockReason.BLOCKED_TITLE -> R.string.toast_blocked_title
        else -> R.string.toast_blocked_shorts
    }

    /**
     * Breadth-first walk of the window, bounded in both node count and depth so
     * a pathological tree can never stall the main thread. Stops as soon as the
     * analyzer says it has seen enough.
     */
    private fun scan(root: AccessibilityNodeInfo, analyzer: ScreenAnalyzer) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            if (analyzer.accept(node.toSignature())) return
            if (depth >= MAX_DEPTH) continue

            for (i in 0 until node.childCount) {
                queue.addLast((node.getChild(i) ?: continue) to depth + 1)
            }
        }
    }

    private fun reloadSettings() {
        enabled = settings.enabled
        config = settings.detectorConfig()
        controller.reset()
    }

    companion object {
        private const val TAG = "NoShorts"

        /** Both the stock app and the common patched build people sideload. */
        val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
        )

        private const val MIN_SCAN_INTERVAL_MS = 150L
        private const val MAX_NODES = 1200
        private const val MAX_DEPTH = 40
    }
}

private fun AccessibilityNodeInfo.toSignature() = NodeSignature(
    viewId = viewIdResourceName,
    text = text?.toString(),
    contentDescription = contentDescription?.toString(),
)
