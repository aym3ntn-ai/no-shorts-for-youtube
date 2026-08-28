# No Shorts for YouTube — Android

The companion to the Firefox extension in the repository root, for the thing a
browser extension can't touch: the **native YouTube app** on Android.

## Why this can't work like the extension

On the web, blocking Shorts is a matter of hiding DOM nodes and intercepting
navigation. The Android app gives you none of that — there is no page to
script, no request you can usefully filter (everything is HTTPS to the same
hosts as regular videos, so Shorts traffic is indistinguishable from any other
watch traffic), and no supported way to modify the app itself without rooting
the phone or patching the APK.

What the app *does* expose is its accessibility tree: the same view hierarchy a
screen reader reads. That tree names its views — `reel_recycler`,
`reel_watch_fragment_root_view`, and friends only exist inside the immersive
Shorts player. So this app runs an `AccessibilityService` that watches for those
view ids and presses Back the instant one shows up. Open Shorts and it closes
itself, fast enough that you never get to the first frame.

This is the same mechanism every no-root screen-time blocker on Android uses.
It needs the accessibility permission, and there is no way around that.

## What it does

- **Closes the Shorts player** the moment it opens, however you got there —
  the bottom-bar Shorts tab, a tile in the home feed, a link from another app,
  or a swipe up from a Short someone sent you.
- **Leaves the rest of YouTube alone.** Shorts *shelves* and tiles on the home
  feed, search results and channel pages are explicitly recognised as entry
  points and never trigger a block, because backing out of the home feed would
  make the app unusable. You'll still see the Shorts row; tapping it just
  bounces you straight back.
- **Escalates if Back doesn't work.** Some deep links stack several Shorts on
  the back stack; after three failed attempts the service goes to the launcher
  instead of pressing Back forever.
- **Blocks videos by title** (optional, off by default) — the same
  blocked-titles list as the browser extension, applied to the watch screen.
- **Swallows `youtube.com/shorts/...` links** (optional, off by default) by
  registering as a handler for them so you can discard the link instead of
  opening YouTube.
- **Quick-settings tile** to pause and resume blocking without opening the app.
- **Survives YouTube updates**, or tells you how to fix it when it doesn't —
  see [When a YouTube update breaks it](#when-a-youtube-update-breaks-it).

It requests **no Android permissions at all** — no internet, no storage. It
never talks to the network, and nothing it reads leaves the device. The only
YouTube-app data it keeps is a short list of Shorts-related view id *names*
(not content), shown to you on the settings screen for troubleshooting.

## Install

There's no Play Store build: Google restricts accessibility-service apps on the
Play Store, and this is a personal sideload.

### Option A — download a build from CI

Every push builds a debug APK. Open the repository's **Actions** tab, pick the
latest **Android** run, and download the `no-shorts-debug-apk` artifact. Unzip
it and transfer the `.apk` to your phone.

### Option B — build it yourself

Requires JDK 17+ and the Android SDK (platform 35). Android Studio has both.

```bash
cd android
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

### Then, on the phone

1. Open the APK and allow installing from unknown sources when prompted.
2. Open **No Shorts**, tap **Open accessibility settings**.
3. Find **No Shorts for YouTube** under *Installed apps* / *Downloaded apps*
   and turn it on. Android will warn you that the service can observe your
   actions — that's the permission it needs to see the Shorts player. It is
   restricted to the YouTube app by `packageNames` in
   [`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml)
   and is never notified about any other app.
4. Open YouTube and tap Shorts. It should bounce you straight back.

On Android 13+ you may also have to allow **Restricted settings** for the app
(App info → ⋮ → *Allow restricted settings*) before the accessibility toggle
will stay on for a sideloaded app.

### Keeping it alive

Aggressive battery managers (Samsung, Xiaomi, OnePlus in particular) will kill
accessibility services. If blocking stops working after a while, set No Shorts
to **Unrestricted** battery usage in App info.

## When a YouTube update breaks it

YouTube renames view ids periodically. When that happens, blocking silently
stops — and you don't have to wait for a release to fix it:

1. Open Shorts once, then open the No Shorts settings screen.
2. Under **Shorts view ids seen on this phone** you'll see the real ids from
   your build of YouTube.
3. Paste the ones that look like the player (anything with `reel_` or `shorts_`
   that isn't a shelf or a tile) into **Extra view id fragments**.

Blocking resumes immediately — no reinstall, no restart. Opening an issue with
that list is also the fastest way to get the default list updated.

There's also an **Aggressive detection** toggle that blocks when a screen has
two independent Shorts signals rather than one known id. It catches renames
automatically, at some risk of a false positive.

## How it's put together

| Module | Role |
|---|---|
| `:detector` | Pure Kotlin/JVM. All of the decision-making: which view ids mean "Shorts player", which mean "just a shelf, leave it alone", title matching, and the debounce/escalation policy. No Android dependencies, so it's covered by ordinary unit tests. |
| `:app` | The Android shell. Walks the accessibility tree into `NodeSignature`s, feeds them to the detector, performs the Back/Home action, and hosts the settings UI, quick-settings tile and link trap. |

Splitting it this way is deliberate: the heuristics are the part most likely to
be wrong or to need tuning, and keeping them off-device means they can be tested
against realistic fake screens (`ShortsDetectorTest`) instead of by hand on a
phone.

```bash
cd android
./gradlew :detector:test   # heuristics
./gradlew :app:lintDebug   # Android lint
```

### Key files

| File | Role |
|---|---|
| [`ShortsDetector.kt`](detector/src/main/kotlin/com/noshorts/detector/ShortsDetector.kt) | View-id marker lists and the screen verdict. |
| [`BlockController.kt`](detector/src/main/kotlin/com/noshorts/detector/BlockController.kt) | Rate limiting and Back → Home escalation. |
| [`ShortsBlockerService.kt`](app/src/main/java/com/noshorts/android/ShortsBlockerService.kt) | The accessibility service and tree walk. |
| [`MainActivity.kt`](app/src/main/java/com/noshorts/android/MainActivity.kt) | Settings, status and diagnostics screen. |

## Known limits

- **Accessibility is required.** There is no unrooted alternative. If you'd
  rather patch the app itself, ReVanced removes Shorts at the source — that's a
  different trade-off (repatching every update, no Play Store updates).
- **Reactive, not preventive.** The service acts once the player has opened, so
  you may see it for a fraction of a second.
- **Title blocking is best-effort.** It only fires on a confirmed watch screen,
  deliberately, so that a matching title in a feed can never leave you unable
  to scroll the home page.
- **The link trap only catches links that reach the app chooser.** Android
  hands verified links straight to YouTube, so it complements the service
  rather than replacing it.
