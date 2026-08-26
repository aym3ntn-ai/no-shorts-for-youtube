const ENABLED_KEY = "noShortsEnabled";
const KEYWORDS_KEY = "blockedKeywords";
const DEFAULT_KEYWORDS = ["Breaking Bad", "The Mentalist", "Suits"];

const checkbox = document.getElementById("toggle");
const keywordsBox = document.getElementById("keywords");
const status = document.getElementById("status");

chrome.storage.local.get([ENABLED_KEY, KEYWORDS_KEY], (res) => {
  checkbox.checked = res[ENABLED_KEY] !== false;
  const keywords = Array.isArray(res[KEYWORDS_KEY])
    ? res[KEYWORDS_KEY]
    : DEFAULT_KEYWORDS;
  keywordsBox.value = keywords.join("\n");
});

checkbox.addEventListener("change", () => {
  chrome.storage.local.set({ [ENABLED_KEY]: checkbox.checked });
});

let saveTimer = null;
keywordsBox.addEventListener("input", () => {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(saveKeywords, 400);
});
keywordsBox.addEventListener("blur", saveKeywords);

function saveKeywords() {
  const keywords = keywordsBox.value
    .split("\n")
    .map((s) => s.trim())
    .filter(Boolean);
  chrome.storage.local.set({ [KEYWORDS_KEY]: keywords }, () => {
    status.textContent = "Saved";
    setTimeout(() => {
      status.textContent = "";
    }, 1000);
  });
}
