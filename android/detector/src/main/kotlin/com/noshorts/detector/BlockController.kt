package com.noshorts.detector

/** What the accessibility service should actually do about a [Verdict]. */
enum class BlockAction { NONE, BACK, HOME }

data class ControllerConfig(
    /**
     * The Shorts player fires a burst of content-changed events; without a
     * cooldown one detection turns into a dozen back presses and the user gets
     * thrown out of the app entirely.
     */
    val cooldownMs: Long = 800,
    /**
     * If backing out this many times in a row still leaves us on Shorts (some
     * deep links push several Shorts onto the stack), go to the launcher
     * instead of pressing back forever.
     */
    val maxConsecutiveBacks: Int = 3,
)

/**
 * Turns a stream of verdicts into a rate-limited, escalating stream of actions.
 * Pure and clock-injected so the debounce behaviour is unit-testable.
 */
class BlockController(private val config: ControllerConfig = ControllerConfig()) {

    private var lastActionAt: Long? = null
    private var consecutiveBacks = 0

    fun onVerdict(verdict: Verdict, nowMs: Long): BlockAction {
        if (verdict is Verdict.Allow) {
            consecutiveBacks = 0
            return BlockAction.NONE
        }

        val last = lastActionAt
        if (last != null && nowMs - last < config.cooldownMs) return BlockAction.NONE

        lastActionAt = nowMs
        return if (consecutiveBacks >= config.maxConsecutiveBacks) {
            consecutiveBacks = 0
            BlockAction.HOME
        } else {
            consecutiveBacks++
            BlockAction.BACK
        }
    }

    fun reset() {
        lastActionAt = null
        consecutiveBacks = 0
    }
}
