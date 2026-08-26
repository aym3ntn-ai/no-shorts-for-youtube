(() => {
  const STORAGE_KEY = "noShortsEnabled";
  let enabled = true;

  const SHORTS_HREF = /^\/shorts\//;

  function isShortsUrl(url) {
    try {
      const u = new URL(url, location.origin);
      return SHORTS_HREF.test(u.pathname);
    } catch {
      return false;
    }
  }

  function redirectAwayFromShorts() {
    if (!enabled) return;
    if (SHORTS_HREF.test(location.pathname)) {
      location.replace("https://www.youtube.com/");
    }
  }

  // Hide any element that is, or contains, a link to a /shorts/ URL, plus
  // known shelf/tab containers. Walks up to a reasonable "card" ancestor so
  // we hide the whole tile, not just the link.
  const CARD_TAGS = new Set([
    "YTD-RICH-ITEM-RENDERER",
    "YTD-VIDEO-RENDERER",
    "YTD-GRID-VIDEO-RENDERER",
    "YTD-COMPACT-VIDEO-RENDERER",
    "YTD-REEL-ITEM-RENDERER",
    "YTM-SHORTS-LOCKUP-VIEW-MODEL",
    "YT-SHORTS-LOCKUP-VIEW-MODEL",
  ]);

  function hideCardFor(el) {
    let node = el;
    let depth = 0;
    while (node && depth < 8) {
      if (CARD_TAGS.has(node.tagName)) {
        node.style.setProperty("display", "none", "important");
        return;
      }
      node = node.parentElement;
      depth++;
    }
    el.style.setProperty("display", "none", "important");
  }

  function sweep() {
    if (!enabled) return;

    document.querySelectorAll('a[href^="/shorts/"]').forEach(hideCardFor);

    document
      .querySelectorAll(
        "ytd-reel-shelf-renderer, ytd-shorts-shelf-renderer, ytd-rich-shelf-renderer[is-shorts], ytm-shorts-lockup-view-model, yt-shorts-lockup-view-model"
      )
      .forEach((el) => el.style.setProperty("display", "none", "important"));

    document
      .querySelectorAll(
        'ytd-guide-entry-renderer a[title="Shorts"], ytd-mini-guide-entry-renderer[aria-label="Shorts"]'
      )
      .forEach((el) => {
        const entry = el.closest(
          "ytd-guide-entry-renderer, ytd-mini-guide-entry-renderer"
        );
        (entry || el).style.setProperty("display", "none", "important");
      });

    document.querySelectorAll("tp-yt-paper-tab, yt-tab-shape").forEach((tab) => {
      const text = tab.textContent && tab.textContent.trim();
      if (text === "Shorts") {
        tab.style.setProperty("display", "none", "important");
      }
    });
  }

  function patchHistory() {
    const fire = () => window.dispatchEvent(new Event("noshorts:navigate"));
    const _push = history.pushState;
    const _replace = history.replaceState;
    history.pushState = function (...args) {
      const ret = _push.apply(this, args);
      fire();
      return ret;
    };
    history.replaceState = function (...args) {
      const ret = _replace.apply(this, args);
      fire();
      return ret;
    };
    window.addEventListener("popstate", fire);
    window.addEventListener("yt-navigate-finish", fire);
    window.addEventListener("noshorts:navigate", () => {
      redirectAwayFromShorts();
      sweep();
    });
  }

  function start() {
    redirectAwayFromShorts();
    sweep();

    const observer = new MutationObserver(() => {
      redirectAwayFromShorts();
      sweep();
    });
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
  }

  patchHistory();

  if (chrome?.storage?.local) {
    chrome.storage.local.get([STORAGE_KEY], (res) => {
      enabled = res[STORAGE_KEY] !== false;
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
      } else {
        start();
      }
    });

    chrome.storage.onChanged.addListener((changes) => {
      if (STORAGE_KEY in changes) {
        enabled = changes[STORAGE_KEY].newValue !== false;
        if (enabled) {
          sweep();
          redirectAwayFromShorts();
        } else {
          location.reload();
        }
      }
    });
  } else {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", start);
    } else {
      start();
    }
  }
})();
