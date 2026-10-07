#!/bin/bash
# Installs the No Shorts extension permanently for every Firefox profile on
# this Mac, using Firefox's enterprise policy file.
#
#   sudo ./install-firefox-macos.sh                 # latest signed GitHub release
#   sudo ./install-firefox-macos.sh path/to/x.xpi   # a signed .xpi you already have
#   sudo ./install-firefox-macos.sh --uninstall
#
# Or straight from GitHub:
#   curl -fsSL https://raw.githubusercontent.com/aym3ntn-ai/no-shorts-for-youtube/main/scripts/install-firefox-macos.sh | sudo bash
#
# Release Firefox only accepts extensions signed by Mozilla, so the .xpi must
# be signed; the script checks before touching anything. Firefox updates can
# replace the app bundle and with it the policy file, so re-run this after an
# update if the extension disappears. It is safe to run any number of times.
set -euo pipefail

ADDON_ID="no-shorts-for-youtube@aym3ntn-ai.github.io"
REPO="aym3ntn-ai/no-shorts-for-youtube"
FIREFOX_APP="${FIREFOX_APP:-/Applications/Firefox.app}"
INSTALL_DIR="${INSTALL_DIR:-/Library/Application Support/NoShortsForYouTube}"
INSTALLED_XPI="$INSTALL_DIR/no-shorts.xpi"
POLICY_DIR="$FIREFOX_APP/Contents/Resources/distribution"
POLICY_FILE="$POLICY_DIR/policies.json"

die() { echo "error: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run with sudo: it writes inside $FIREFOX_APP"
[ -d "$FIREFOX_APP" ] || die "Firefox not found at $FIREFOX_APP (set FIREFOX_APP to its path)"

ours() { [ -f "$POLICY_FILE" ] && grep -q "\"$ADDON_ID\"" "$POLICY_FILE"; }

if [ "${1:-}" = "--uninstall" ]; then
  if ours; then
    rm -f "$POLICY_FILE"
    echo "Removed $POLICY_FILE"
  elif [ -f "$POLICY_FILE" ]; then
    echo "Left $POLICY_FILE alone: it doesn't mention No Shorts."
  fi
  rm -rf "$INSTALL_DIR"
  echo "Done. Restart Firefox; the extension stays installed in each profile"
  echo "until you remove it from about:addons."
  exit 0
fi

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

if [ -n "${1:-}" ]; then
  [ -f "$1" ] || die "no such file: $1"
  xpi="$1"
else
  echo "Looking up the latest signed Firefox release..."
  tag=$(curl -fsSL "https://api.github.com/repos/$REPO/releases?per_page=100" \
        | grep -o '"tag_name": *"firefox-v[^"]*"' | head -1 | sed 's/.*"\(firefox-v[^"]*\)"$/\1/') || true
  [ -n "$tag" ] || die "no signed Firefox release published yet; pass a signed .xpi instead"
  xpi="$workdir/no-shorts.xpi"
  curl -fsSL -o "$xpi" \
    "https://github.com/$REPO/releases/download/$tag/no-shorts-for-youtube-$tag.xpi" \
    || die "could not download the $tag release asset"
  echo "Downloaded $tag"
fi

# Refuse an unsigned build up front: Firefox would silently ignore it, which
# looks exactly like the install not working.
listing=$(unzip -l "$xpi" 2>/dev/null) || die "$xpi is not a valid extension package"
grep -qE 'META-INF/(cose\.sig|mozilla\.rsa)' <<<"$listing" \
  || die "$xpi is not signed by Mozilla; release Firefox won't load it"
unzip -p "$xpi" manifest.json 2>/dev/null | grep -q "\"$ADDON_ID\"" \
  || die "$xpi is not the No Shorts extension (expected id $ADDON_ID)"

# Never clobber a policy file someone else wrote; merging JSON safely without
# extra tools isn't worth the risk.
if [ -f "$POLICY_FILE" ] && ! ours; then
  die "$POLICY_FILE already exists with other policies. Add this to its
\"policies\" object by hand, then re-run:
  \"ExtensionSettings\": {\"$ADDON_ID\": {\"installation_mode\": \"normal_installed\",
    \"install_url\": \"file://$INSTALLED_XPI\"}}"
fi

mkdir -p "$INSTALL_DIR" "$POLICY_DIR"
install -m 644 "$xpi" "$INSTALLED_XPI"
# A file:// URL must percent-encode the space in "Application Support".
url="file://${INSTALLED_XPI// /%20}"
cat > "$POLICY_FILE" <<EOF
{
  "policies": {
    "ExtensionSettings": {
      "$ADDON_ID": {
        "installation_mode": "normal_installed",
        "install_url": "$url"
      }
    }
  }
}
EOF
chmod 644 "$POLICY_FILE"

echo "Installed. Quit Firefox completely (Cmd+Q) and reopen it."
echo "No Shorts then installs into every profile; about:policies shows the policy."
