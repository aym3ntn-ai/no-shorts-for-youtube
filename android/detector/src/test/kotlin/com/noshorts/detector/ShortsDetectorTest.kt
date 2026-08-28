package com.noshorts.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PKG = "com.google.android.youtube:id/"

private fun id(name: String, text: String? = null, desc: String? = null) =
    NodeSignature(viewId = PKG + name, text = text, contentDescription = desc)

/** A plausible slice of the immersive Shorts player's node tree. */
private val shortsPlayerScreen = listOf(
    id("watch_while_activity"),
    id("reel_watch_fragment_root_view"),
    id("reel_recycler"),
    id("reel_progress_bar"),
    NodeSignature(text = "Shorts"),
)

/** Home feed: a Shorts shelf, the bottom-bar tab, and a filter chip. */
private val homeFeedScreen = listOf(
    id("watch_while_activity"),
    id("browse_fragment"),
    id("reel_shelf_container", text = "Shorts"),
    id("reel_item_thumbnail"),
    id("shorts_lockup_view"),
    id("pivot_bar_shorts_tab", desc = "Shorts"),
    id("chip_cloud_chip", text = "Shorts"),
)

/** Regular watch screen for a video the user has blocked by title. */
private val watchScreen = listOf(
    id("watch_while_activity"),
    id("player_fragment_container"),
    id("video_title", text = "Suits: LA - Season 1 Trailer"),
    id("channel_name", text = "Peacock"),
)

class ShortsDetectorTest {

    private val default = DetectorConfig()

    @Test
    fun `shorts player is blocked`() {
        val analysis = analyzeScreen(shortsPlayerScreen, default)
        val verdict = analysis.verdict as Verdict.Block
        assertEquals(BlockReason.SHORTS_PLAYER, verdict.reason)
        // The first player marker encountered wins, since the walk stops there.
        assertEquals("reel_watch_fragment_root_view", verdict.evidence)
    }

    @Test
    fun `home feed with a shorts shelf is left alone`() {
        assertEquals(Verdict.Allow, analyzeScreen(homeFeedScreen, default).verdict)
    }

    @Test
    fun `home feed is left alone even in aggressive mode`() {
        val aggressive = DetectorConfig(aggressive = true)
        assertEquals(Verdict.Allow, analyzeScreen(homeFeedScreen, aggressive).verdict)
    }

    @Test
    fun `regular watch screen is left alone`() {
        assertEquals(Verdict.Allow, analyzeScreen(watchScreen, default).verdict)
    }

    @Test
    fun `unrecognised ids can be blocked via extra markers`() {
        val futureBuild = listOf(id("watch_while_activity"), id("xq_immersive_pager"))
        assertEquals(Verdict.Allow, analyzeScreen(futureBuild, default).verdict)

        val patched = DetectorConfig(extraPlayerMarkers = listOf("xq_immersive_pager"))
        val verdict = analyzeScreen(futureBuild, patched).verdict as Verdict.Block
        assertEquals(BlockReason.SHORTS_PLAYER, verdict.reason)
    }

    @Test
    fun `extra markers are trimmed and case-insensitive`() {
        val screen = listOf(id("XQ_Immersive_Pager"))
        val config = DetectorConfig(extraPlayerMarkers = listOf("  XQ_IMMERSIVE_PAGER  ", "", "   "))
        assertTrue(analyzeScreen(screen, config).verdict is Verdict.Block)
    }

    @Test
    fun `aggressive mode blocks on two weak signals`() {
        // Renamed player ids we don't know, but "reel", "shorts" and the label
        // all show up outside any known entry-point container.
        val renamedPlayer = listOf(
            id("reel_surface_root"),
            id("shorts_overlay_buttons"),
            NodeSignature(text = "Shorts"),
        )
        assertEquals(Verdict.Allow, analyzeScreen(renamedPlayer, default).verdict)

        val verdict = analyzeScreen(renamedPlayer, DetectorConfig(aggressive = true)).verdict
        assertEquals(BlockReason.SHORTS_PLAYER_HEURISTIC, (verdict as Verdict.Block).reason)
    }

    @Test
    fun `a lone shorts label never blocks`() {
        val screen = listOf(id("browse_fragment"), NodeSignature(text = "Shorts"))
        assertEquals(Verdict.Allow, analyzeScreen(screen, DetectorConfig(aggressive = true)).verdict)
    }

    @Test
    fun `blocked title on a watch screen is blocked`() {
        val config = DetectorConfig(
            titleBlockingEnabled = true,
            blockedTitles = listOf("Breaking Bad", "Suits"),
        )
        val verdict = analyzeScreen(watchScreen, config).verdict as Verdict.Block
        assertEquals(BlockReason.BLOCKED_TITLE, verdict.reason)
        assertEquals("Suits: LA - Season 1 Trailer", verdict.evidence)
    }

    @Test
    fun `title matching is off unless enabled`() {
        val config = DetectorConfig(blockedTitles = listOf("Suits"))
        assertEquals(Verdict.Allow, analyzeScreen(watchScreen, config).verdict)
    }

    @Test
    fun `a matching title outside a watch screen is ignored`() {
        // The same title as a tile in a feed must not trigger a back press,
        // or the home feed becomes unusable.
        val feed = listOf(
            id("browse_fragment"),
            id("video_title", text = "Suits: LA - Season 1 Trailer"),
        )
        val config = DetectorConfig(titleBlockingEnabled = true, blockedTitles = listOf("Suits"))
        assertEquals(Verdict.Allow, analyzeScreen(feed, config).verdict)
    }

    @Test
    fun `blank blocked titles do not match everything`() {
        val config = DetectorConfig(titleBlockingEnabled = true, blockedTitles = listOf("", "   "))
        assertEquals(Verdict.Allow, analyzeScreen(watchScreen, config).verdict)
    }

    @Test
    fun `shorts ids are collected for diagnostics`() {
        val ids = analyzeScreen(homeFeedScreen, default).observedShortsIds
        assertTrue(ids.contains("reel_shelf_container"))
        assertTrue(ids.contains("shorts_lockup_view"))
        assertTrue(ids.none { it == "browse_fragment" })
    }

    @Test
    fun `analysis stops early once the player is found`() {
        val analyzer = ScreenAnalyzer(default)
        assertTrue(shortsPlayerScreen.any { analyzer.accept(it) })
    }

    @Test
    fun `view ids without a package prefix still normalise`() {
        assertEquals("reel_recycler", normalizeViewId("reel_recycler"))
        assertEquals("reel_recycler", normalizeViewId("com.google.android.youtube:id/reel_recycler"))
        assertEquals(null, normalizeViewId(null))
        assertEquals(null, normalizeViewId("   "))
    }
}

class BlockControllerTest {

    private val block = Verdict.Block(BlockReason.SHORTS_PLAYER, "reel_recycler")

    @Test
    fun `first block presses back`() {
        val controller = BlockController()
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 0))
    }

    @Test
    fun `repeat events inside the cooldown are ignored`() {
        val controller = BlockController(ControllerConfig(cooldownMs = 800))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 0))
        assertEquals(BlockAction.NONE, controller.onVerdict(block, 100))
        assertEquals(BlockAction.NONE, controller.onVerdict(block, 799))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 800))
    }

    @Test
    fun `escalates to home when back keeps failing`() {
        val controller = BlockController(ControllerConfig(cooldownMs = 100, maxConsecutiveBacks = 3))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 0))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 100))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 200))
        assertEquals(BlockAction.HOME, controller.onVerdict(block, 300))
        // and starts over rather than spamming HOME
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 400))
    }

    @Test
    fun `leaving shorts resets the escalation counter`() {
        val controller = BlockController(ControllerConfig(cooldownMs = 100, maxConsecutiveBacks = 3))
        repeat(3) { controller.onVerdict(block, it * 100L) }
        assertEquals(BlockAction.NONE, controller.onVerdict(Verdict.Allow, 400))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 500))
        assertEquals(BlockAction.BACK, controller.onVerdict(block, 600))
    }

    @Test
    fun `allow never acts`() {
        val controller = BlockController()
        assertEquals(BlockAction.NONE, controller.onVerdict(Verdict.Allow, 0))
    }
}
