const STORAGE_KEY = "noShortsEnabled";
const checkbox = document.getElementById("toggle");

chrome.storage.local.get([STORAGE_KEY], (res) => {
  checkbox.checked = res[STORAGE_KEY] !== false;
});

checkbox.addEventListener("change", () => {
  chrome.storage.local.set({ [STORAGE_KEY]: checkbox.checked });
});
