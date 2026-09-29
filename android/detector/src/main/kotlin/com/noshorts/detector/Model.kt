package com.noshorts.detector

/**
 * A flattened view of a single node in the YouTube app's accessibility tree.
 *
 * The Android service adapts `AccessibilityNodeInfo` into this; tests build it
 * by hand. Keeping the detector free of Android types is what lets the entire
 * blocking heuristic be unit-tested on the JVM.
 */
data class NodeSignature(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
)

/** Which heuristic fired, for logging and for the in-app diagnostics screen. */
enum class BlockReason {
    /** A view id unique to the immersive Shorts player was on screen. */
    SHORTS_PLAYER,

    /** Two or more weak Shorts signals lined up (aggressive mode only). */
    SHORTS_PLAYER_HEURISTIC,

    /** A watch page whose title matched the user's blocked-title list. */
    BLOCKED_TITLE,
}

sealed interface Verdict {
    /** Nothing to do — the current screen is fine. */
    data object Allow : Verdict

    /** Back out of this screen. [evidence] is the id/title that tripped it. */
    data class Block(val reason: BlockReason, val evidence: String) : Verdict
}

/** The result of a single pass over one screen. */
data class Analysis(
    val verdict: Verdict,
    /**
     * Every Shorts-ish view id seen on this screen, normalized. Surfaced in the
     * app's diagnostics screen so a user on a YouTube build we don't recognise
     * can copy the ids into "extra markers" instead of waiting for a release.
     */
    val observedShortsIds: Set<String> = emptySet(),
)
