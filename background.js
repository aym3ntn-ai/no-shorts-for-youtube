const STORAGE_KEY = "noShortsEnabled";
const KEYWORDS_KEY = "blockedKeywords";
const DEFAULT_KEYWORDS = ["Breaking Bad", "The Mentalist", "Suits"];

function isShortsUrl(url) {
  try {
    const u = new URL(url);
    return /^\/shorts\//.test(u.pathname);
  } catch {
    return false;
  }
}

chrome.webNavigation.onBeforeNavigate.addListener(
  (details) => {
    if (details.frameId !== 0) return;
    if (!isShortsUrl(details.url)) return;

    chrome.storage.local.get([STORAGE_KEY], (res) => {
      if (res[STORAGE_KEY] === false) return;
      chrome.tabs.update(details.tabId, { url: "https://www.youtube.com/" });
    });
  },
  { url: [{ hostContains: "youtube.com" }] }
);

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.get([STORAGE_KEY, KEYWORDS_KEY], (res) => {
    const updates = {};
    if (res[STORAGE_KEY] === undefined) updates[STORAGE_KEY] = true;
    if (res[KEYWORDS_KEY] === undefined) updates[KEYWORDS_KEY] = DEFAULT_KEYWORDS;
    if (Object.keys(updates).length > 0) {
      chrome.storage.local.set(updates);
    }
  });
});
