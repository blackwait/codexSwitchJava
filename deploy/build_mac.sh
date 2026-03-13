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
ICON_SOURCE_ICNS="$PROJECT_DIR/src/main/resources/assets/icon_app.icns"
ICON_ICNS="$TARGET_DIR/CodexSwitcher.icns"

cd "$PROJECT_DIR"

mkdir -p "$JAVA_FX_CACHE"

mvn -q -DskipTests clean package

if [[ -f "$ICON_SOURCE_ICNS" ]]; then
  cp "$ICON_SOURCE_ICNS" "$ICON_ICNS"
else
  if [[ ! -f "$ICON_PNG" ]]; then
    echo "Icon source not found: $ICON_PNG" >&2
    exit 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required to build the macOS icon." >&2
    exit 1
  fi
  python3 - "$ICON_PNG" "$ICON_ICNS" <<'PY'
from PIL import Image
import sys
src, dst = sys.argv[1], sys.argv[2]
img = Image.open(src).convert("RGBA")
img.save(dst, sizes=[(16, 16), (32, 32), (64, 64), (128, 128), (256, 256), (512, 512), (1024, 1024)])
PY
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

APP_PATH="$DIST_DIR/$APP_NAME.app"
rm -rf "$APP_PATH"
ditto "$APP_IMAGE_DIR/$APP_NAME.app" "$APP_PATH"

DMG_PATH="$DIST_DIR/$APP_NAME-$APP_VERSION.dmg"
rm -f "$DMG_PATH"
if jpackage "${DMG_ARGS[@]}"; then
  mv "$DMG_DIR/$APP_NAME-$APP_VERSION.dmg" "$DMG_PATH"
else
  echo "Warning: dmg build failed in current environment, app-image was kept." >&2
fi

if [[ -d "$APP_PATH" ]] && command -v xattr >/dev/null 2>&1; then
  xattr -dr com.apple.quarantine "$APP_PATH" 2>/dev/null || true
fi

echo "Mac app image: $APP_PATH"
if [[ -f "$DMG_PATH" ]]; then
  echo "Mac installer: $DMG_PATH"
fi
