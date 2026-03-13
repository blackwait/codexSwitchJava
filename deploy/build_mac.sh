#!/bin/zsh

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
TARGET_DIR="$PROJECT_DIR/target"
DIST_DIR="$PROJECT_DIR/dist/mac"
APP_IMAGE_DIR="$TARGET_DIR/app-image"
DMG_DIR="$TARGET_DIR/dmg"
APP_NAME="CodexSwitcher"
MAIN_CLASS="com.codexswitcher.app.Launcher"
JAVA_FX_CACHE="/tmp/CodexSwitcher-javafx-cache"
ICON_PNG="$PROJECT_DIR/src/main/resources/assets/icon_app.png"
ICONSET_DIR="$TARGET_DIR/CodexSwitcher.iconset"
ICON_ICNS="$TARGET_DIR/CodexSwitcher.icns"

cd "$PROJECT_DIR"

mkdir -p "$JAVA_FX_CACHE"

mvn -q -DskipTests clean package

if [[ ! -f "$ICON_PNG" ]]; then
  echo "Icon source not found: $ICON_PNG" >&2
  exit 1
fi

if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
  echo "sips and iconutil are required to build the macOS icon." >&2
  exit 1
fi

rm -rf "$ICONSET_DIR" "$ICON_ICNS"
mkdir -p "$ICONSET_DIR"
sips -z 16 16 "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
sips -z 32 32 "$ICON_PNG" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
sips -z 32 32 "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
sips -z 64 64 "$ICON_PNG" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
sips -z 128 128 "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
sips -z 256 256 "$ICON_PNG" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
sips -z 256 256 "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
sips -z 512 512 "$ICON_PNG" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
sips -z 512 512 "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
sips -z 1024 1024 "$ICON_PNG" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null

if ! iconutil -c icns "$ICONSET_DIR" -o "$ICON_ICNS"; then
  echo "Failed to build macOS icon: $ICON_ICNS" >&2
  exit 1
fi

VERSION=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1 | tr -d '\r')
APP_VERSION="${VERSION%-SNAPSHOT}"
MAIN_JAR="codex-switcher-javafx-${VERSION}.jar"

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"
rm -rf "$APP_IMAGE_DIR"
mkdir -p "$APP_IMAGE_DIR"
rm -rf "$DMG_DIR"
mkdir -p "$DMG_DIR"

JPACKAGE_ARGS=(
  --type app-image
  --dest "$APP_IMAGE_DIR"
  --input "$TARGET_DIR/jpackage-input"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-Djavafx.cachedir=$JAVA_FX_CACHE"
)

JPACKAGE_ARGS+=(--icon "$ICON_ICNS")

DMG_ARGS=(
  --type dmg
  --dest "$DMG_DIR"
  --input "$TARGET_DIR/jpackage-input"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-Djavafx.cachedir=$JAVA_FX_CACHE"
)

DMG_ARGS+=(--icon "$ICON_ICNS")

jpackage "${JPACKAGE_ARGS[@]}"
jpackage "${DMG_ARGS[@]}"

APP_PATH="$DIST_DIR/$APP_NAME.app"
rm -rf "$APP_PATH"
ditto "$APP_IMAGE_DIR/$APP_NAME.app" "$APP_PATH"
mv "$DMG_DIR/$APP_NAME-$APP_VERSION.dmg" "$DIST_DIR/"

if [[ -d "$APP_PATH" ]] && command -v xattr >/dev/null 2>&1; then
  xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null || true
fi

echo "Mac app image: $APP_PATH"
echo "Mac installer: $DIST_DIR/$APP_NAME-$APP_VERSION.dmg"
