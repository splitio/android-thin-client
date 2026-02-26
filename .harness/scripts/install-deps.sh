#!/usr/bin/env bash
set -euo pipefail

# Ensure Android SDK env vars exist even if the pipeline runner doesn't predefine them.
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
# Install profile controls how much Android setup is performed:
# - unit: Java + SDK components required for local unit tests
# - full/instrumented: includes system image + emulator setup
INSTALL_PROFILE="${INSTALL_PROFILE:-full}"
# Only hard-fail on missing emulator when explicitly required by a pipeline.
REQUIRE_EMULATOR="${REQUIRE_EMULATOR:-false}"

echo "========================================"
echo "  Android Platform Dependencies Setup"
echo "========================================"
echo ""
echo "Install profile: $INSTALL_PROFILE"
echo ""

# ============================================
# 1. Install Java 17
# ============================================
echo "=== Installing Java 17 ==="

# Detect if we need sudo (not running as root)
if [ "$EUID" -eq 0 ] || [ "$(id -u)" -eq 0 ]; then
  SUDO=""
else
  SUDO="sudo"
fi

# Install Java 17
echo "Installing OpenJDK 17..."
$SUDO apt-get update -qq
$SUDO apt-get install -y openjdk-17-jdk curl unzip ca-certificates

# Resolve a Java 17 installation explicitly (works across amd64/arm64 and avoids old defaults)
JAVA17_BIN="$(find /usr/lib/jvm -maxdepth 3 -type f -path "*/java-17-openjdk-*/bin/java" 2>/dev/null | head -n1 || true)"
if [ -z "$JAVA17_BIN" ]; then
  echo "ERROR: Could not locate Java 17 binary after installation"
  exit 1
fi
export JAVA_HOME="$(dirname "$(dirname "$JAVA17_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"

# Update alternatives to use Java 17 as default
#$SUDO update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
#$SUDO update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac

echo ""
echo "Java 17 installation complete:"
java -version
echo "JAVA_HOME: $JAVA_HOME"

echo "✓ Java 17 installation complete"
echo ""

# ============================================
# 2. Setup Android SDK Tools
# ============================================
echo "=== Setting up Android SDK Tools ==="

# Ensure SDK directory exists
mkdir -p "$ANDROID_HOME"

# Look for command-line tools in common locations
CMDLINE_TOOLS_BIN=""
if [ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then
  CMDLINE_TOOLS_BIN="$ANDROID_HOME/cmdline-tools/latest/bin"
  echo "✓ Found command-line tools at: $CMDLINE_TOOLS_BIN"
elif [ -d "$ANDROID_HOME/tools/bin" ]; then
  CMDLINE_TOOLS_BIN="$ANDROID_HOME/tools/bin"
  echo "✓ Found legacy tools at: $CMDLINE_TOOLS_BIN"
else
  echo "⚠ Command-line tools not found, installing..."
  TMP_DIR="$(mktemp -d)"

  # Download Linux command-line tools package
  CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  
  echo "Downloading from: $CMDLINE_TOOLS_URL"
  curl -fSL -o "$TMP_DIR/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"

  # Extract and organize (zip layout can vary; locate sdkmanager dynamically)
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR"
  SDKMANAGER_BIN="$(find "$TMP_DIR" -type f -path "*/bin/sdkmanager" | head -n1 || true)"
  if [ -z "$SDKMANAGER_BIN" ]; then
    echo "ERROR: Unable to locate sdkmanager in downloaded command-line tools package"
    exit 1
  fi
  EXTRACTED_TOOLS_ROOT="$(dirname "$(dirname "$SDKMANAGER_BIN")")"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  cp -R "$EXTRACTED_TOOLS_ROOT/." "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$TMP_DIR"
  
  CMDLINE_TOOLS_BIN="$ANDROID_HOME/cmdline-tools/latest/bin"
  echo "✓ Installed command-line tools at: $CMDLINE_TOOLS_BIN"
fi

# Update PATH to include all Android tools
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$CMDLINE_TOOLS_BIN"

# Test that sdkmanager is accessible
echo ""
echo "Testing SDK Manager access..."
SDKMANAGER="$CMDLINE_TOOLS_BIN/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
fi
if [ -z "${SDKMANAGER:-}" ]; then
  echo "ERROR: SDK Manager binary was not found"
  exit 1
fi
"$SDKMANAGER" --version

echo "✓ Android SDK tools setup complete"
echo ""

# ============================================
# 3. Install Android Components
# ============================================
echo "=== Installing required Android components ==="

# Update PATH to include Android tools
if [ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then
  export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
elif [ -d "$ANDROID_HOME/tools/bin" ]; then
  export PATH="$PATH:$ANDROID_HOME/tools/bin"
fi
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

# Verify SDK Manager is accessible
if [ -z "${SDKMANAGER:-}" ] || [ ! -x "$SDKMANAGER" ]; then
  echo "ERROR: SDK Manager not found in PATH"
  echo "PATH: $PATH"
  exit 1
fi

echo "SDK Manager version: $("$SDKMANAGER" --version)"
echo ""

# Accept licenses first
echo "Accepting Android SDK licenses..."
yes | "$SDKMANAGER" --licenses 2>&1 || echo "Licenses already accepted"
echo ""

# Get list of installed packages
INSTALLED_PACKAGES=$("$SDKMANAGER" --list_installed 2>/dev/null)

# Install platform-tools
if ! echo "$INSTALLED_PACKAGES" | grep -q "platform-tools"; then
  echo "Installing platform-tools..."
  "$SDKMANAGER" "platform-tools"
else
  echo "✓ platform-tools already installed"
fi

# Install build-tools
if ! echo "$INSTALLED_PACKAGES" | grep -q "build-tools;"; then
  echo "Installing build-tools..."
  "$SDKMANAGER" "build-tools;34.0.0"
else
  echo "✓ build-tools already installed"
fi

# Install Android platform
if ! echo "$INSTALLED_PACKAGES" | grep -q "platforms;android-29"; then
  echo "Installing Android platform 29..."
  "$SDKMANAGER" "platforms;android-29"
else
  echo "✓ Android platform 29 already installed"
fi

if [ "$INSTALL_PROFILE" = "unit" ]; then
  echo ""
  echo "Skipping system-image and emulator installation for unit-test profile"
else
  # Install system image based on machine architecture
  HOST_ARCH="$(uname -m)"
  if [ "$HOST_ARCH" = "aarch64" ] || [ "$HOST_ARCH" = "arm64" ]; then
    SYSTEM_IMAGE="system-images;android-29;default;arm64-v8a"
  else
    SYSTEM_IMAGE="system-images;android-29;default;x86_64"
  fi
  echo ""
  echo "Using system image: $SYSTEM_IMAGE"
  if ! echo "$INSTALLED_PACKAGES" | grep -q "$SYSTEM_IMAGE"; then
    echo "Installing system image: $SYSTEM_IMAGE"
    yes | "$SDKMANAGER" "$SYSTEM_IMAGE" 2>&1 || true
  else
    echo "✓ System image already installed: $SYSTEM_IMAGE"
  fi

  # Install/update emulator (force update to ensure we have a working version)
  echo "Installing/updating Android emulator..."
  yes | "$SDKMANAGER" "emulator" 2>&1 || true

  # Verify emulator binary is accessible and working
  export PATH="$PATH:$ANDROID_HOME/emulator"
  if ! command -v emulator >/dev/null 2>&1; then
    echo "WARNING: emulator binary not found after installation"
    echo "Contents of ANDROID_HOME/emulator:"
    ls -la "$ANDROID_HOME/emulator" || echo "Directory does not exist"
    if [ "$REQUIRE_EMULATOR" = "true" ]; then
      echo "ERROR: Emulator is required for this pipeline but is unavailable"
      exit 1
    fi
  fi

  # Test emulator can show version (quick check it's not corrupted)
  echo "Testing emulator binary..."
  if command -v emulator >/dev/null 2>&1 && emulator -version 2>&1 | head -1 | grep -q "emulator"; then
    echo "✓ Emulator binary is working:"
    emulator -version | head -3
  else
    echo "WARNING: Emulator binary may have issues or is unavailable"
    emulator -version 2>&1 | head -5 || true
  fi
fi

echo ""
echo "Refreshing SDK package cache..."
# Force package list refresh so avdmanager recognizes newly installed packages
"$SDKMANAGER" --list > /dev/null 2>&1 || echo "Warning: Failed to refresh package list"

echo ""
echo "=== Android SDK components installed ==="
echo ""
echo "Installed packages:"
"$SDKMANAGER" --list_installed

echo ""
echo "========================================"
echo "  ✓ Android Dependencies Complete"
echo "========================================"
