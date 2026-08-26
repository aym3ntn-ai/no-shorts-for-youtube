const STORAGE_KEY = "noShortsEnabled";

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
  chrome.storage.local.get([STORAGE_KEY], (res) => {
    if (res[STORAGE_KEY] === undefined) {
      chrome.storage.local.set({ [STORAGE_KEY]: true });
    }
  });
});
