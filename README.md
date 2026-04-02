# AndroidUtil

A command-line utility for Android developers to analyze, convert, sign APK/AAB files and manage connected devices.

---

## Features

- **APK/AAB Analysis** — Manifest, certificates, size breakdown, DEX info, 16KB alignment
- **AAB to APK Conversion** — Universal or device-specific with optional signing
- **Signature Verification** — v1/v2/v3/v4 scheme verification with certificate details
- **Native Library Inspector** — ABI, ELF class, strip status, 16KB page alignment check
- **Deeplink Analysis** — Extract app links, custom schemes, auto-verify status
- **Google Play Compatibility** — Target SDK, 16KB alignment, 64-bit, sensitive permissions
- **Size & Manifest Diff** — Compare two versions side by side
- **Stack Trace Decoder** — Decode obfuscated traces with ProGuard/R8 mapping
- **ADB Integration** — Install, uninstall, screenshot, mirror, deeplink testing
- **HTML Reports** — Generate standalone analysis reports
- **Interactive Mode** — Menu-driven interface for all operations
- **Multi-language UI** — Turkish and English (`--lang tr` / `--lang en`)

---

## Requirements

- **JDK 17+**
- **Android SDK** (for aapt2, apksigner, adb features)
  - Set the `ANDROID_HOME` environment variable
- **scrcpy** (optional — for screen mirroring, auto-downloaded if missing)
- **apkeep** (optional — for Play Store APK download)

---

## Installation

### Download Pre-built JAR

Download the latest release from [GitHub Releases](https://github.com/umutcansu/AndroidUtils/releases):

```bash
# Download (replace version as needed)
curl -LO https://github.com/umutcansu/AndroidUtils/releases/download/v1.0.0/androidutil-1.0.0.jar

# Run
java -jar androidutil-1.0.0.jar analyze app.apk

# Optional: create an alias
alias androidutil='java -jar /path/to/androidutil-1.0.0.jar'
```

### Build from Source

```bash
git clone https://github.com/umutcansu/AndroidUtils.git
cd AndroidUtils
./gradlew shadowJar

# The JAR is at build/libs/androidutil-1.0.0.jar
alias androidutil='java -jar build/libs/androidutil-1.0.0.jar'
```

---

## Usage

### Language

```bash
# Turkish (default)
androidutil --lang tr analyze app.apk

# English
androidutil --lang en analyze app.apk

# Or set via environment variable
export ANDROIDUTIL_LANG=en
androidutil analyze app.apk
```

### Interactive Mode

```bash
# Launch interactive menu (no arguments)
androidutil

# With language and options
androidutil --lang en --json
```

Interactive mode provides a menu-driven interface where you can:
1. Select an APK/AAB file (file picker, recent files, or paste path)
2. Choose operations from the menu
3. Compare files, run ADB commands, generate reports

---

### CLI Commands

#### analyze

Full APK/AAB analysis including manifest, size breakdown, DEX info, alignment, signatures, and permissions.

```bash
androidutil analyze app.apk
androidutil analyze app.aab

# JSON output
androidutil --json analyze app.apk

# Verbose
androidutil -v analyze app.apk
```

**Example output:**
```
APK Analysis: app.apk
File size: 6.3 MB (6,578,032 bytes)

Manifest
 Package    com.example.app
 Version    2.1.0 (42)
 Min SDK    24
 Target SDK 34

Size Breakdown
 Component         Size      Percentage
 DEX               1.2 MB    19.0%
 Resources         3.1 MB    49.2%
 Native Libraries  1.5 MB    23.8%
 Asset             0.3 MB    4.8%
 Other             0.2 MB    3.2%
 Total             6.3 MB    100.0%
```

#### convert

Convert AAB to APK (universal or device-specific), with optional signing.

```bash
# Universal APK
androidutil convert app.aab --universal

# Device-specific APK set
androidutil convert app.aab

# Custom output path
androidutil convert app.aab --universal -o output.apk

# With signing
androidutil convert app.aab --universal \
  --keystore release.jks \
  --keystore-password mypass \
  --key-alias mykey \
  --key-password keypass
```

#### deeplink

Extract and list deeplinks from an APK/AAB.

```bash
androidutil deeplink app.apk
```

**Example output:**
```
Deeplink Analysis: app.apk
Total deeplinks: 5

App Links (verified, 2)
 URL                              Activity       Auto Verify
 https://example.com/path         MainActivity   YES

Custom Schemes (3)
 URL                              Activity
 myapp://home                     MainActivity
 myapp://profile/{id}             ProfileActivity
```

#### nativelib

Inspect native libraries (.so) — ABI, size, ELF class, 16KB alignment, strip status.

```bash
androidutil nativelib app.apk
```

**Example output:**
```
Native Library Report
Total: 24 libraries, 4.2 MB
ABIs: arm64-v8a (8), armeabi-v7a (8), x86 (4), x86_64 (4)
16KB Alignment: ALL 16KB COMPATIBLE

 Library         ABI          Size    Uncompressed  Strip  ELF     16KB
 libapp.so       arm64-v8a    512 KB  1.1 MB        Yes    64-bit  OK
 libapp.so       armeabi-v7a  384 KB  768 KB        Yes    32-bit  ---
```

#### playcheck

Check Google Play Store compatibility.

```bash
androidutil playcheck app.apk
```

**Example output:**
```
Google Play Compatibility: PLAY STORE READY
Pass: 5 | Fail: 0 | Warn: 1

 Check                Status  Detail
 Target SDK           OK      targetSdk=34 (>= 34)
 16KB Page Alignment  OK      All 64-bit libraries are 16KB compatible (8 .so)
 Sensitive Permissions WARN   3 sensitive permissions detected
 Min SDK              OK      minSdk=24 (>= 21)
 64-bit Support       OK      64-bit ABI available: arm64-v8a
```

#### sizediff

Compare sizes between two APK/AAB files with category breakdown and top changes.

```bash
androidutil sizediff old.apk new.apk
```

#### mdiff

Compare manifest fields (version, SDK levels, components, permissions) between two files.

```bash
androidutil mdiff old.apk new.apk
```

#### diff

Compare permissions between two APK/AAB files — shows added, removed, and unchanged.

```bash
androidutil diff old.apk new.apk
```

#### sign

Sign or verify APK signatures.

```bash
# Verify APK signature
androidutil sign verify app.apk

# Sign an APK
androidutil sign apk app.apk \
  --keystore release.jks \
  --keystore-password mypass \
  --key-alias mykey
```

#### decode

Decode obfuscated stack traces using ProGuard/R8 mapping file.

```bash
androidutil decode stacktrace.txt -m mapping.txt
```

#### keystore

Inspect keystore files — aliases, certificates, fingerprints.

```bash
androidutil keystore info release.jks -p mypassword
```

#### resources

List resources and assets with categories, sizes, and largest files.

```bash
androidutil resources app.apk
```

#### adb

ADB device operations.

```bash
# Install APK to device
androidutil adb install app.apk

# Test deeplink on device
androidutil adb deeplink "myapp://home"

# Take screenshot
androidutil adb screenshot output.png

# Clear app data
androidutil adb clear com.example.app

# Uninstall app
androidutil adb uninstall com.example.app
```

#### mirror

Mirror device screen to desktop using scrcpy.

```bash
androidutil mirror
androidutil mirror --stay-awake --turn-screen-off
```

#### HTML Report

Available in interactive mode: select a file, then choose **report** from the menu.

---

## Global Options

| Option | Description |
|--------|-------------|
| `--lang <tr\|en>` | UI language (default: `tr`) |
| `--json` | Output in JSON format |
| `-v, --verbose` | Verbose output |
| `-V, --version` | Show version |

**Environment variables** (prefix `ANDROIDUTIL_`):
- `ANDROIDUTIL_LANG` — Default language
- `ANDROIDUTIL_JSON` — Enable JSON output
- `ANDROIDUTIL_VERBOSE` — Enable verbose output

---

## GitHub Mirror

To set up a GitHub mirror of the GitLab repository:

### Option 1: GitLab Push Mirror (Recommended)

In GitLab, go to **Settings > Repository > Mirroring repositories**:

1. Enter the GitHub URL: `https://<GITHUB_TOKEN>@github.com/umutcansugroup/androidutils.git`
2. Direction: **Push**
3. Authentication method: **Password** — use a GitHub Personal Access Token (with `repo` scope)
4. Check **Mirror only protected branches** if you only want `main` mirrored
5. Click **Mirror repository**

GitLab will automatically push changes to GitHub after every push.

### Option 2: Manual Dual Remote

Add GitHub as a second remote and push to both:

```bash
# Add GitHub remote
git remote set-url --add --push origin https://github.com/umutcansugroup/androidutils.git

# Keep GitLab as a push target too
git remote set-url --add --push origin https://192.168.1.80:8930/umutcansugroup/androidutils.git

# Now `git push` pushes to both
git push
```

### Option 3: Separate Named Remotes

```bash
git remote add github https://github.com/umutcansugroup/androidutils.git
git remote add gitlab https://192.168.1.80:8930/umutcansugroup/androidutils.git

# Push to each individually
git push gitlab main
git push github main

# Or push to both with a script / CI step
```

### Option 4: GitHub Actions (Pull Mirror)

Create `.github/workflows/mirror.yml` in the GitHub repo:

```yaml
name: Mirror from GitLab
on:
  schedule:
    - cron: '0 */6 * * *'  # every 6 hours
  workflow_dispatch:

jobs:
  mirror:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Pull from GitLab
        run: |
          git remote add gitlab https://192.168.1.80:8930/umutcansugroup/androidutils.git
          git fetch gitlab main
          git merge gitlab/main --ff-only
          git push origin main
```

> **Note:** For Option 4, the GitHub Actions runner must be able to reach `192.168.1.80:8930` (which is a local network address). This only works if you have a self-hosted runner on the same network, or if the GitLab instance is publicly accessible.

---

## Project Structure

```
src/main/kotlin/com/androidutil/
  Main.kt                    # Entry point (interactive / CLI mode)
  i18n/Messages.kt           # i18n ResourceBundle wrapper
  cli/                       # Clikt CLI commands
  core/                      # Feature modules
    analyzer/                #   APK/AAB analysis
    converter/               #   AAB to APK conversion
    adb/                     #   ADB device operations
    signing/                 #   Certificate & signature
    diff/                    #   Size, manifest, permission diff
    deeplink/                #   Deeplink extraction
    nativelib/               #   Native library inspection
    playcompat/              #   Play Store compatibility
    resources/               #   Resource listing
    dex/                     #   DEX file analysis
    elf/                     #   ELF binary parsing
    stacktrace/              #   Stack trace decoding
    download/                #   APK downloading
    scrcpy/                  #   Screen mirroring
  output/                    # Renderers (terminal, JSON, HTML)
  sdk/                       # Android SDK tool locator
  util/                      # Utilities
```

---

## Building

```bash
# Build shadow JAR
./gradlew shadowJar

# Run tests
./gradlew test

# Run directly
./gradlew run --args="analyze app.apk"
```

---

## License

This project is proprietary software of UmutCansu Group.
