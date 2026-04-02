# AndroidUtil

Android developer utility tool for APK/AAB analysis, conversion, signing, and device management.

**AndroidUtil**, APK ve AAB dosyalarini analiz etmek, donusturmek, imzalamak ve cihaz yonetimi icin gelistirilmis bir komut satiri aracidir.

---

## Features / Ozellikler

- **APK/AAB Analysis** - Manifest, certificates, size breakdown, DEX info, 16KB alignment
- **AAB to APK Conversion** - Universal or device-specific with optional signing
- **Signature Verification** - v1/v2/v3/v4 scheme verification with certificate details
- **Native Library Inspector** - ABI, ELF class, strip status, 16KB page alignment
- **Deeplink Analysis** - Extract app links, custom schemes, auto-verify status
- **Google Play Compatibility** - Target SDK, 16KB alignment, 64-bit, sensitive permissions
- **Size & Manifest Diff** - Compare two versions side by side
- **Stack Trace Decoder** - Decode obfuscated traces with ProGuard/R8 mapping
- **ADB Integration** - Install, uninstall, screenshot, mirror, deeplink testing
- **HTML Reports** - Generate standalone analysis reports
- **Interactive Mode** - Menu-driven interface for all operations
- **Multi-language** - Turkish and English UI (`--lang tr` / `--lang en`)

---

## Requirements / Gereksinimler

- **JDK 17+**
- **Android SDK** (for aapt2, apksigner, adb features)
  - Set `ANDROID_HOME` environment variable
- **scrcpy** (optional, for screen mirroring - auto-downloaded if missing)
- **apkeep** (optional, for Play Store APK download)

---

## Installation / Kurulum

```bash
# Clone and build
git clone https://192.168.1.80:8930/umutcansugroup/androidutils.git
cd androidutils
./gradlew shadowJar

# The JAR is at build/libs/androidutil-1.0.0.jar
alias androidutil='java -jar build/libs/androidutil-1.0.0.jar'
```

---

## Usage / Kullanim

### Language / Dil

```bash
# Turkish (default)
androidutil --lang tr analyze app.apk

# English
androidutil --lang en analyze app.apk

# Or set via environment variable
export ANDROIDUTIL_LANG=en
androidutil analyze app.apk
```

### Interactive Mode / Interaktif Mod

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

### CLI Commands / CLI Komutlari

#### Analyze / Analiz

```bash
# Full APK analysis (manifest, size, DEX, alignment, signatures, permissions)
androidutil analyze app.apk
androidutil analyze app.aab

# JSON output
androidutil --json analyze app.apk

# Verbose output
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

#### Convert / Donustur

```bash
# AAB to universal APK
androidutil convert app.aab --universal

# AAB to device-specific APK set
androidutil convert app.aab

# With custom output path
androidutil convert app.aab --universal -o output.apk

# With signing
androidutil convert app.aab --universal \
  --keystore release.jks \
  --keystore-password mypass \
  --key-alias mykey \
  --key-password keypass
```

#### Deeplink Analysis / Deeplink Analizi

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

#### Native Library Inspector

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

#### Google Play Compatibility / Google Play Uyumlulugu

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

#### Size Comparison / Boyut Karsilastirma

```bash
androidutil sizediff old.apk new.apk
```

#### Manifest Comparison / Manifest Karsilastirma

```bash
androidutil mdiff old.apk new.apk
```

#### Permission Diff / Izin Farki

```bash
androidutil diff old.apk new.apk
```

#### Signature Verification / Imza Dogrulama

```bash
# Verify APK signature
androidutil sign verify app.apk

# Sign an APK
androidutil sign apk app.apk \
  --keystore release.jks \
  --keystore-password mypass \
  --key-alias mykey
```

#### Stack Trace Decoder

```bash
androidutil decode stacktrace.txt -m mapping.txt
```

#### Keystore Information / Keystore Bilgileri

```bash
androidutil keystore info release.jks -p mypassword
```

#### Resource Listing / Kaynak Listesi

```bash
androidutil resources app.apk
```

#### ADB Commands / ADB Komutlari

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

# Mirror screen (scrcpy)
androidutil mirror
androidutil mirror --stay-awake --turn-screen-off
```

#### HTML Report / HTML Rapor

Available in interactive mode: select a file, then choose **report** from the menu.

---

## Global Options

| Option | Description |
|--------|-------------|
| `--lang <tr\|en>` | UI language (default: tr) |
| `--json` | Output in JSON format |
| `-v, --verbose` | Verbose output |
| `-V, --version` | Show version |

Environment variables (prefix `ANDROIDUTIL_`):
- `ANDROIDUTIL_LANG` - Default language
- `ANDROIDUTIL_JSON` - Enable JSON output
- `ANDROIDUTIL_VERBOSE` - Enable verbose output

---

## Project Structure

```
src/main/kotlin/com/androidutil/
  Main.kt                    # Entry point (interactive/CLI mode detection)
  i18n/Messages.kt           # i18n ResourceBundle wrapper
  cli/                       # Clikt CLI commands
  core/                      # Feature modules
    analyzer/                # APK/AAB analysis
    converter/               # AAB to APK conversion
    adb/                     # ADB device operations
    signing/                 # Certificate & signature
    diff/                    # Size, manifest, permission diff
    deeplink/                # Deeplink extraction
    nativelib/               # Native library inspection
    playcompat/              # Play Store compatibility
    resources/               # Resource listing
    dex/                     # DEX file analysis
    elf/                     # ELF binary parsing
    stacktrace/              # Stack trace decoding
    download/                # APK downloading
    scrcpy/                  # Screen mirroring
  output/                    # Renderers (terminal, JSON, HTML)
  sdk/                       # Android SDK tool locator
  util/                      # Utilities
```

---

## Building / Derleme

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
