# INSTALLATION GUIDE — SETBD SCANNER

## 1. Install the prebuilt APK (recommended)

1. Open the repo → **Releases** → download `app-debug.apk` (or grab the latest
   Actions artifact `setbd-scanner-apk`).
2. On your Android 7.0+ device, allow *Install unknown apps* for your browser
   or file manager.
3. Install the APK and open **SETBD Scanner**.

No special permissions are requested beyond INTERNET — all data stays on the
device (SQLite + app-private storage).

## 2. Build from source (Linux / macOS / WSL)

Requirements: JDK 17+, Android SDK (platform 34, build-tools 34.0.0).

```bash
git clone https://github.com/ai-multitool-v1/Setbd-scanner.git
cd Setbd-scanner
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed with debug key for quick installs
```

## 3. CI build (already configured)

`.github/workflows/android-build.yml` builds automatically on every push to
`main` and on manual dispatch. Artifacts: `app-debug.apk` + `app-release.apk`.
The release workflow step also attaches APKs to the `v1.0-latest` release.

## 4. Release signing (for distribution)

The CI release APK is signed with the debug key so it installs anywhere. For a
production release create your own keystore:

```bash
keytool -genkey -v -keystore setbd.jks -keyalg RSA -keysize 2048 -validity 10000 -alias setbd
apksigner sign --ks setbd.jks --out setbd-signed.apk app-release-unsigned.apk
```

and configure `signingConfigs` in `app/build.gradle`.

## 5. Enable external tools (optional) via Termux

TERMUX-status tools are documented in-app with exact commands. Quick start:

```bash
# Install Termux from F-Droid (the Play Store build is outdated)
pkg update
pkg install nmap nikto hydra whois dnsutils traceroute android-tools
# Go-based tools need Go:
pkg install golang
go install -v github.com/projectdiscovery/subfinder/v2/cmd/subfinder@latest
go install -v github.com/projectdiscovery/nuclei/v2/cmd/nuclei@latest
# Python-based tools:
pkg install python
pip install theHarvester dirsearch
```

The app detects whether Termux is installed (Home → System Compatibility) and
links each tool page to its setup commands. **The app never claims an external
tool ran when it didn't.**

## 6. First scan in 60 seconds

1. **Scanner tab** → enter `https://your-own-site.com`
2. Tick the modules (Recon / Headers / TLS / Ports / Crawl) and check
   *"I am authorized to test this target"*.
3. **Start scan** → watch the live console → **Open last report** →
   export **PDF**.
