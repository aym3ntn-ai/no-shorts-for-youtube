package com.noshorts.android

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import com.noshorts.detector.DetectorConfig

/**
 * Every user-facing knob, backed by SharedPreferences so the settings screen,
 * the quick-settings tile and the accessibility service all see the same state.
 */
class Settings(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Fall back to the two-weak-signal heuristic when ids aren't recognised. */
    var aggressive: Boolean
        get() = prefs.getBoolean(KEY_AGGRESSIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_AGGRESSIVE, value).apply()

    var showToast: Boolean
        get() = prefs.getBoolean(KEY_TOAST, true)
        set(value) = prefs.edit().putBoolean(KEY_TOAST, value).apply()

    var titleBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TITLE_BLOCKING, false)
        set(value) = prefs.edit().putBoolean(KEY_TITLE_BLOCKING, value).apply()

    /** Mirrors the browser extension's blocked-titles list, one per line. */
    var blockedTitles: List<String>
        get() = prefs.getString(KEY_TITLES, null)?.toLines() ?: DEFAULT_BLOCKED_TITLES
        set(value) = prefs.edit().putString(KEY_TITLES, value.joinToString("\n")).apply()

    /** Extra view-id fragments, for YouTube builds newer than this release. */
    var extraMarkers: List<String>
        get() = prefs.getString(KEY_MARKERS, null)?.toLines() ?: emptyList()
        set(value) = prefs.edit().putString(KEY_MARKERS, value.joinToString("\n")).apply()

    var blockedCount: Int
        get() = prefs.getInt(KEY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_COUNT, value).apply()

    fun recordBlock() {
        blockedCount += 1
    }

    /**
     * Shorts-ish view ids seen on this device, newest first. Lets a user on an
     * unrecognised YouTube build read the real ids off their own phone and paste
     * them into [extraMarkers].
     */
    var observedIds: List<String>
        get() = prefs.getString(KEY_OBSERVED, null)?.toLines() ?: emptyList()
        private set(value) = prefs.edit().putString(KEY_OBSERVED, value.joinToString("\n")).apply()

    fun recordObservedIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = observedIds
        if (current.containsAll(ids)) return
        observedIds = (ids + current).distinct().take(MAX_OBSERVED_IDS)
    }

    fun clearObservedIds() {
        observedIds = emptyList()
    }

    fun detectorConfig() = DetectorConfig(
        extraPlayerMarkers = extraMarkers,
        aggressive = aggressive,
        titleBlockingEnabled = titleBlockingEnabled,
        blockedTitles = blockedTitles,
    )

    /** Whether the youtube.com/shorts link trap is registered with the system. */
    var linkTrapEnabled: Boolean
        get() = context.packageManager.getComponentEnabledSetting(linkTrapComponent()) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        set(value) {
            val state = if (value) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                linkTrapComponent(),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }

    private fun linkTrapComponent() =
        ComponentName(context.applicationContext, ShortsLinkTrapActivity::class.java)

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS = "no_shorts"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AGGRESSIVE = "aggressive"
        private const val KEY_TOAST = "toast"
        private const val KEY_TITLE_BLOCKING = "title_blocking"
        private const val KEY_TITLES = "blocked_titles"
        private const val KEY_MARKERS = "extra_markers"
        private const val KEY_COUNT = "blocked_count"
        private const val KEY_OBSERVED = "observed_ids"
        private const val MAX_OBSERVED_IDS = 40

        /** Same seed list as the browser extension, for parity. */
        val DEFAULT_BLOCKED_TITLES = listOf("Breaking Bad", "The Mentalist", "Suits")

        /** True when the user has switched our service on in system settings. */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ShortsBlockerService::class.java)
            val enabledServices = AndroidSettings.Secure.getString(
                context.contentResolver,
                AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            for (entry in splitter) {
                val component = ComponentName.unflattenFromString(entry) ?: continue
                if (component == expected) return true
            }
            return false
        }
    }
}

private fun String.toLines(): List<String> =
    split('\n').map { it.trim() }.filter { it.isNotEmpty() }
