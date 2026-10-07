<p align="center">
  <img src="assets/logo.png" width="120" height="120" alt="No Shorts for YouTube logo">
</p>

<h1 align="center">No Shorts for YouTube</h1>

<p align="center">
  Hides YouTube Shorts and any videos you don't want to see, then blocks direct navigation to their pages — in Firefox, and in the native YouTube app on Android.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Firefox%20140%2B-orange" alt="Firefox 140+">
  <img src="https://img.shields.io/badge/platform-Android%208%2B-3ddc84" alt="Android 8+">
  <img src="https://img.shields.io/badge/manifest-v3-blue" alt="Manifest V3">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License">
</p>

---

## Why

YouTube Shorts is everywhere on the site now — the home feed, the sidebar,
search results, channel pages — and there's no built-in way to turn it off.
This extension removes it from the UI and stops you from ever landing on a
Shorts URL, whether you clicked a link, typed one, or YouTube's own
single-page app tried to route you there. It does the same for any video
whose title matches a list of shows you'd rather not stumble into — handy
for avoiding spoilers or steering clear of a show entirely.

## Two halves

| | |
|---|---|
| **Firefox extension** (this folder) | youtube.com in the browser. Hides Shorts from the UI and redirects away from `/shorts/` URLs. |
| **[Android app](android/)** (`android/`) | The native YouTube app, where an extension can't reach. An accessibility service spots the Shorts player and closes it the moment it opens. |

They're independent — install either or both. The rest of this page covers the
extension; see [`android/README.md`](android/README.md) for the phone.

## Features

- **Hides Shorts UI**: the sidebar "Shorts" entry, the Shorts shelf on the
  home/subscriptions feed, Shorts tiles in search results and grids, and the
  "Shorts" tab on channel pages.
- **Blocks Shorts navigation**: any attempt to open a `/shorts/...` URL —
  typed, clicked, bookmarked, or triggered by YouTube's client-side
  router — redirects back to youtube.com.
- **Blocks videos by title**: maintain a list of show names or keywords
  (e.g. `Breaking Bad`, `The Mentalist`, `Suits`) from the popup. Any video
  tile whose title contains one, anywhere on the site, is hidden; if you
  land directly on a matching watch page, it redirects home. Matching is
  case-insensitive and matches substrings, so `Suits` also catches spinoffs
  like `Suits: LA`.
- **Survives YouTube's SPA routing**: a `MutationObserver` plus a patched
  `history.pushState`/`popstate` listener re-run the hiding logic every time
  YouTube navigates without a full page reload.
- **One-click toggle**: turn blocking on/off from the toolbar popup, no
  need to reload the extension.
- **No data collection, no network requests**: everything runs locally in
  the page; the extension talks to nothing but your browser's storage.

## Install

### From source (temporary, resets on Firefox restart)

1. Clone this repo.
2. Open `about:debugging#/runtime/this-firefox` in Firefox.
3. Click **Load Temporary Add-on…** and select `manifest.json` from the
   cloned folder.
4. Visit youtube.com — Shorts should be hidden, and any Shorts link/URL
   redirects to the homepage.

### Permanent install (signed)

Firefox only installs signed extensions permanently. Download the `.xpi` from
the latest `firefox-v*` entry on the
[Releases](https://github.com/aym3ntn-ai/no-shorts-for-youtube/releases) page
and open it in Firefox — it's signed by Mozilla, so it installs and stays
installed.

Releases are built and signed by
[`firefox-release.yml`](.github/workflows/firefox-release.yml); see
[Releasing](#releasing) to cut one.

### Every profile on a Mac, in one command

[`scripts/install-firefox-macos.sh`](scripts/install-firefox-macos.sh) installs
the signed extension for all Firefox profiles via an enterprise policy:

```bash
curl -fsSL https://raw.githubusercontent.com/aym3ntn-ai/no-shorts-for-youtube/main/scripts/install-firefox-macos.sh | sudo bash
```

With no argument it fetches the latest signed `firefox-v*` release; pass a
path to use a signed `.xpi` you already have
(`sudo bash install-firefox-macos.sh ~/Downloads/x.xpi`). It refuses unsigned
or foreign packages and never overwrites someone else's `policies.json`.
Re-run it if a Firefox update removes the extension; `--uninstall` undoes it.

### Build it yourself

```bash
npx web-ext lint --source-dir .     # AMO validation
npx web-ext build --source-dir .    # unsigned zip in web-ext-artifacts/
```

An unsigned build can only be loaded temporarily (`about:debugging`), or
permanently in Firefox Developer Edition / Nightly with
`xpinstall.signatures.required` set to `false` in `about:config`.

Both commands read [`web-ext-config.mjs`](web-ext-config.mjs), which keeps the
Android app, CI config and docs out of the package — without it the `.xpi`
picks up the whole `android/` tree.

## Usage

Click the toolbar icon to open the popup:

- Flip **Block Shorts** to enable or disable the whole extension.
- Edit the **Blocked titles** box, one show or keyword per line. It saves
  automatically as you type (debounced) and on blur.

## How it works

| File | Role |
|---|---|
| `content.js` / `content.css` | Hide Shorts shelves, sidebar entries, grid tiles, the channel "Shorts" tab, and any video tile whose title matches the blocked-titles list. Redirect home from a Shorts page or a matching watch page. Re-run on every SPA navigation. |
| `background.js` | Uses `webNavigation.onBeforeNavigate` to catch direct navigations to `/shorts/...` and redirect the tab before it loads. Seeds the default blocked-titles list on install. |
| `popup.html` / `popup.js` | Toolbar toggle and blocked-titles editor, backed by `chrome.storage.local`, read by both scripts above. |

Title matching happens in the content script after the page's title element
is available, since neither the URL nor `webNavigation` can tell us a
video's title before it loads — so a blocked watch page can flash briefly
before the redirect fires.

## Permissions

| Permission | Why |
|---|---|
| `storage` | Persist the on/off toggle. |
| `webNavigation` | Detect and redirect navigations to `/shorts/...` before the page loads. |
| `*://*.youtube.com/*` (host permission) | Run the content script and navigation guard only on YouTube. |

## The Android app

The browser extension can't do anything about the YouTube app on your phone —
there's no DOM to rewrite and no request worth filtering. The `android/` folder
holds a small companion app that solves it the only way an unrooted phone
allows: an accessibility service that watches the YouTube app's view hierarchy
and presses Back the instant the Shorts player appears.

It requests no Android permissions, makes no network requests, and is scoped to
the YouTube app alone. Build and install instructions, plus what to do when a
YouTube update renames things, are in [`android/README.md`](android/README.md).

## Releasing

Releases are signed by Mozilla through the AMO API and published to GitHub
Releases. One-time setup: create an API credential at
[addons.mozilla.org/developers/addon/api/key](https://addons.mozilla.org/en-US/developers/addon/api/key/)
and add it to the repository as the secrets `WEB_EXT_API_KEY` and
`WEB_EXT_API_SECRET`.

To cut a release, bump `version` in `manifest.json` and merge to `main`.
That's all: the workflow signs the package and publishes a GitHub Release
tagged `firefox-v<version>`, creating the tag itself. It runs whenever the
extension's own files change on `main`, and does nothing if that version was
already released (AMO rejects a duplicate version). To release without a
new commit, run **Firefox Release** manually from the Actions tab. Pushing a
`firefox-vX.Y.Z` tag by hand also works.

The workflow lints, checks any pushed tag agrees with `manifest.json`, signs,
and attaches the signed `.xpi` to the release. The `firefox-` prefix keeps
these tags distinct from the Android app's `v*.*.*` tags, so neither release
triggers the other.

By default it signs on AMO's **unlisted** channel: Mozilla signs the build but
doesn't host it, which is what makes self-distribution from GitHub Releases
work. To instead publish the add-on publicly on addons.mozilla.org, run the
workflow manually from the Actions tab with the `listed` channel — AMO then
hosts and distributes it, so no `.xpi` is attached to the GitHub Release.

> **Add-on ID:** the add-on's identity on AMO is the
> `browser_specific_settings.gecko.id` in `manifest.json`,
> `no-shorts-for-youtube@aym3ntn-ai.github.io`. AMO ties an ID to the account
> that first signs it, permanently: an ID already registered by someone else
> fails with `403 Forbidden`, and this one must not change once released, or
> Firefox treats the new build as a different add-on.

## Contributing

Issues and pull requests are welcome — YouTube changes its markup often, so
selector fixes in `content.css` / `content.js` are especially useful. Please
run the linter before submitting:

```bash
npx web-ext lint --source-dir .
```

## License

[MIT](LICENSE)
