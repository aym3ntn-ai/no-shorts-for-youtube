package com.noshorts.detector

/**
 * Tuning for [ScreenAnalyzer]. Everything here is user-visible in the app's
 * settings screen, because YouTube renames view ids often enough that a
 * hard-coded list alone would rot.
 */
data class DetectorConfig(
    /** Extra id fragments from the app's "advanced" field, merged with the defaults. */
    val extraPlayerMarkers: List<String> = emptyList(),
    /** Allow the weaker two-signal heuristic to trigger a block. */
    val aggressive: Boolean = false,
    /** Also back out of watch pages whose title matches [blockedTitles]. */
    val titleBlockingEnabled: Boolean = false,
    /** Case-insensitive substrings, mirroring the browser extension's list. */
    val blockedTitles: List<String> = emptyList(),
) {
    internal val playerMarkers: List<String> =
        (DEFAULT_PLAYER_MARKERS + extraPlayerMarkers.map { it.trim().lowercase() })
            .filter { it.isNotEmpty() }
            .distinct()

    internal val titleNeedles: List<String> =
        blockedTitles.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

private enum class WeakSignal { REEL_ID, SHORTS_ID, SHORTS_LABEL }

/**
 * View id fragments that only ever appear inside the full-screen Shorts player.
 * Matching is substring-based on the part of the id after `:id/`, so
 * `com.google.android.youtube:id/reel_recycler` matches `reel_recycler`.
 */
internal val DEFAULT_PLAYER_MARKERS = listOf(
    "reel_recycler",
    "reel_watch_player",
    "reel_watch_fragment",
    "reel_player_page",
    "reel_player_underlay",
    "reel_playback",
    "reel_progress_bar",
    "reel_time_bar",
    "reel_action_panel",
    "reel_metapanel",
    "reel_meta_panel",
    "reel_dyn_",
    "shorts_video_title",
    "shorts_player",
    "shorts_immersive",
)

/**
 * Ids that mention reels/shorts but belong to *entry points* — the shelf on the
 * home feed, a grid tile, the bottom-bar tab, a filter chip. Backing out of
 * those screens would fight the user on the home feed, so any id matching one
 * of these is ignored entirely by the block heuristics.
 */
internal val ENTRY_POINT_MARKERS = listOf(
    "reel_shelf",
    "reel_item",
    "reel_lockup",
    "reel_grid",
    "reel_thumbnail",
    "shorts_shelf",
    "shorts_lockup",
    "shorts_entry",
    "shorts_tab",
    "pivot_bar",
    "pivot_shorts",
    "tab_shorts",
    "nav_shorts",
    "bottom_bar",
    "chip",
)

/** Ids that mark "this is the regular video watch screen". */
internal val WATCH_SCREEN_MARKERS = listOf(
    "watch_while",
    "watch_player",
    "watch_fragment",
    "player_fragment",
    "player_view",
    "player_control",
    "video_metadata",
)

/** Ids that hold the title of the video currently open on the watch screen. */
internal val TITLE_MARKERS = listOf(
    "video_title",
    "watch_title",
    "player_title",
    "title_text",
)

/**
 * Reduces one screen's worth of nodes to a [Verdict].
 *
 * Feed nodes to [accept] as you walk the tree; it returns `true` as soon as the
 * answer is certain so the caller can stop walking early. Then read [analysis].
 */
class ScreenAnalyzer(private val config: DetectorConfig) {

    private var strongEvidence: String? = null
    private val weakSignals = mutableSetOf<WeakSignal>()
    private val observedIds = linkedSetOf<String>()
    private var watchScreen = false
    private var titleEvidence: String? = null

    /** @return true once a strong match makes further walking pointless. */
    fun accept(node: NodeSignature): Boolean {
        val id = normalizeViewId(node.viewId)

        if (id != null) {
            if (id.mentionsShorts()) observedIds += id

            if (!id.matchesAny(ENTRY_POINT_MARKERS)) {
                if (id.matchesAny(config.playerMarkers)) {
                    strongEvidence = id
                    return true
                }
                if (id.contains("reel")) weakSignals += WeakSignal.REEL_ID
                if (id.contains("shorts")) weakSignals += WeakSignal.SHORTS_ID
            }

            if (id.matchesAny(WATCH_SCREEN_MARKERS)) watchScreen = true

            if (config.titleBlockingEnabled && titleEvidence == null && id.matchesAny(TITLE_MARKERS)) {
                node.text?.let { title ->
                    if (matchesBlockedTitle(title, config.titleNeedles)) titleEvidence = title.trim()
                }
            }
        }

        if (node.text.isShortsLabel() || node.contentDescription.isShortsLabel()) {
            weakSignals += WeakSignal.SHORTS_LABEL
        }

        return false
    }

    val analysis: Analysis
        get() = Analysis(verdict(), observedIds.toSet())

    private fun verdict(): Verdict {
        strongEvidence?.let { return Verdict.Block(BlockReason.SHORTS_PLAYER, it) }

        if (config.aggressive && weakSignals.size >= 2) {
            return Verdict.Block(
                BlockReason.SHORTS_PLAYER_HEURISTIC,
                weakSignals.sorted().joinToString("+") { it.name.lowercase() },
            )
        }

        // Only ever trip on a confirmed watch screen: a stray match on the home
        // feed would leave the user unable to scroll it at all.
        titleEvidence?.let { if (watchScreen) return Verdict.Block(BlockReason.BLOCKED_TITLE, it) }

        return Verdict.Allow
    }
}

/** Convenience entry point for tests and one-shot analysis. */
fun analyzeScreen(nodes: Iterable<NodeSignature>, config: DetectorConfig): Analysis {
    val analyzer = ScreenAnalyzer(config)
    for (node in nodes) if (analyzer.accept(node)) break
    return analyzer.analysis
}

/** `com.google.android.youtube:id/reel_recycler` -> `reel_recycler`. */
internal fun normalizeViewId(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return trimmed.substringAfterLast('/').lowercase().ifEmpty { null }
}

internal fun matchesBlockedTitle(title: String?, needles: List<String>): Boolean {
    if (title.isNullOrBlank() || needles.isEmpty()) return false
    val haystack = title.lowercase()
    return needles.any { haystack.contains(it) }
}

private fun String.matchesAny(markers: List<String>) = markers.any { this.contains(it) }

private fun String.mentionsShorts() = contains("reel") || contains("shorts")

private fun String?.isShortsLabel() = this?.trim()?.equals("shorts", ignoreCase = true) == true
