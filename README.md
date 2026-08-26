<p align="center">
  <img src="assets/logo.png" width="120" height="120" alt="No Shorts for YouTube logo">
</p>

<h1 align="center">No Shorts for YouTube</h1>

<p align="center">
  A small Firefox extension that hides YouTube Shorts everywhere and blocks direct navigation to Shorts pages.
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
single-page app tried to route you there.

## Features

- **Hides Shorts UI**: the sidebar "Shorts" entry, the Shorts shelf on the
  home/subscriptions feed, Shorts tiles in search results and grids, and the
  "Shorts" tab on channel pages.
- **Blocks Shorts navigation**: any attempt to open a `/shorts/...` URL —
  typed, clicked, bookmarked, or triggered by YouTube's client-side
  router — redirects back to youtube.com.
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

Click the toolbar icon and flip **Block Shorts** to enable or disable the
extension without removing it.

## How it works

| File | Role |
|---|---|
| `content.js` / `content.css` | Hide Shorts shelves, sidebar entries, grid tiles, and the channel "Shorts" tab. Re-run on every SPA navigation. |
| `background.js` | Uses `webNavigation.onBeforeNavigate` to catch direct navigations to `/shorts/...` and redirect the tab before it loads. |
| `popup.html` / `popup.js` | Toolbar toggle, backed by `chrome.storage.local`, read by both scripts above. |

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
