// Shared by local `web-ext` runs and the release workflow, so a build made by
// hand and a build made by CI contain exactly the same files.
//
// The repo root holds more than the extension (the Android app, CI config,
// docs), and web-ext packages the whole source dir by default — without this
// the .xpi balloons to tens of megabytes of Kotlin and Gradle output.
export default {
  ignoreFiles: [
    "android/**",
    ".github/**",
    "scripts/**",
    "assets/**",
    "web-ext-artifacts/**",
    "*.md",
    "web-ext-config.mjs",
  ],
};
