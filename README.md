<p align="center">
  <img src="assets/logo.png" width="120" height="120" alt="No Shorts for YouTube logo">
</p>

<h1 align="center">No Shorts for YouTube</h1>

<p align="center">
  A small Firefox extension that hides YouTube Shorts and any videos you don't want to see, then blocks direct navigation to their pages.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Firefox%20109%2B-orange" alt="Firefox 109+">
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

### Permanent install

Firefox requires extensions to be signed by Mozilla to install permanently.
To build a distributable, signed package:

```bash
npx web-ext build --source-dir .
```

Submit the generated zip under `web-ext-artifacts/` at
[addons.mozilla.org/developers](https://addons.mozilla.org/developers/) for
signing (choose **On your own** if you don't want it listed publicly), then
install the signed `.xpi`. Alternatively, load it unsigned in Firefox
Developer Edition or Nightly by setting `xpinstall.signatures.required` to
`false` in `about:config`.

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

## Contributing

Issues and pull requests are welcome — YouTube changes its markup often, so
selector fixes in `content.css` / `content.js` are especially useful. Please
run the linter before submitting:

```bash
npx web-ext lint --source-dir .
```

## License

[MIT](LICENSE)
