(() => {
  const ENABLED_KEY = "noShortsEnabled";
  const KEYWORDS_KEY = "blockedKeywords";
  const DEFAULT_KEYWORDS = ["Breaking Bad", "The Mentalist", "Suits"];

  let enabled = true;
  let keywords = DEFAULT_KEYWORDS;

  const SHORTS_HREF = /^\/shorts\//;
  const HOME_URL = "https://www.youtube.com/";

  function textMatchesBlocklist(text) {
    if (!text) return false;
    const lower = text.toLowerCase();
    return keywords.some((kw) => kw && lower.includes(kw.toLowerCase()));
  }

  function redirectHome() {
    location.replace(HOME_URL);
  }

  function redirectAwayFromShorts() {
    if (!enabled) return;
    if (SHORTS_HREF.test(location.pathname)) {
      redirectHome();
    }
  }

  function redirectAwayFromBlockedShow() {
    if (!enabled) return;
    if (location.pathname !== "/watch") return;

    const heading = document.querySelector(
      "h1.ytd-watch-metadata yt-formatted-string, ytd-watch-metadata h1 yt-formatted-string, h1.title yt-formatted-string"
    );
    const title =
      (heading && heading.textContent) ||
      document.title.replace(/ - YouTube$/, "");

    if (textMatchesBlocklist(title)) {
      redirectHome();
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
    "YTD-PLAYLIST-VIDEO-RENDERER",
    "YTD-PLAYLIST-PANEL-VIDEO-RENDERER",
    "YTD-COMPACT-PLAYLIST-RENDERER",
    "YTM-SHORTS-LOCKUP-VIEW-MODEL",
    "YT-SHORTS-LOCKUP-VIEW-MODEL",
    "YT-LOCKUP-VIEW-MODEL",
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

  const TITLE_SELECTORS = [
    "#video-title",
    "a#video-title-link",
    ".yt-lockup-metadata-view-model-wiz__title",
  ].join(", ");

  function hideBlockedTitles() {
    if (keywords.length === 0) return;
    document.querySelectorAll(TITLE_SELECTORS).forEach((el) => {
      const text = el.getAttribute("title") || el.textContent || "";
      if (textMatchesBlocklist(text)) {
        hideCardFor(el);
      }
    });
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

    hideBlockedTitles();
  }

  function guard() {
    redirectAwayFromShorts();
    redirectAwayFromBlockedShow();
    sweep();
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
    window.addEventListener("noshorts:navigate", guard);
  }

  function start() {
    guard();

    const observer = new MutationObserver(guard);
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
  }

  patchHistory();

  if (chrome?.storage?.local) {
    chrome.storage.local.get([ENABLED_KEY, KEYWORDS_KEY], (res) => {
      enabled = res[ENABLED_KEY] !== false;
      keywords = Array.isArray(res[KEYWORDS_KEY])
        ? res[KEYWORDS_KEY]
        : DEFAULT_KEYWORDS;
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
      } else {
        start();
      }
    });

    chrome.storage.onChanged.addListener((changes) => {
      let shouldReload = false;

      if (ENABLED_KEY in changes) {
        const wasEnabled = enabled;
        enabled = changes[ENABLED_KEY].newValue !== false;
        if (wasEnabled && !enabled) shouldReload = true;
      }
      if (KEYWORDS_KEY in changes) {
        keywords = Array.isArray(changes[KEYWORDS_KEY].newValue)
          ? changes[KEYWORDS_KEY].newValue
          : DEFAULT_KEYWORDS;
      }

      if (shouldReload) {
        location.reload();
      } else {
        guard();
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
