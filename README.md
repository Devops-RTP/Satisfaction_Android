# Satisfaction Kiosk (Android)

A full-screen, anonymous 1–5 smiley satisfaction survey for Samsung (or any Android 8.0+) tablets.
One tap records a rating and shows a thank-you screen. The app saves each rating on the tablet first, then uploads it to your database or web API, and retries automatically if the network is down.

## Build the APK

**Option A: GitHub (no local tools needed)**
Push this folder to a GitHub repo. The included workflow (`.github/workflows/build-apk.yml`) builds the APK. Download it from the run's **Artifacts** section.

**Option B: Android Studio**
Open the folder, wait for Gradle sync, then choose **Build > Build App Bundle(s)/APK(s) > Build APK(s)**.
Command line: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

The release build is signed with the debug key so it installs right away. Use your own keystore for wider distribution.

## Install on the tablet
1. Copy the APK to the tablet and open it. Allow "Install unknown apps" for your file manager when Android asks.
2. Launch **Satisfaction Kiosk**. On first launch it creates the config file at:
   `Internal storage/Android/data/ca.rtp.satisfaction/files/kiosk.ini`
3. Edit that file over USB from a PC (it shows up in Windows Explorer), then reload it in the app (see Admin menu).

## Admin menu
Tap the **top-right corner 5 times** within 3 seconds, then enter the PIN (`admin_pin`, default **2468**. Change it!).
The menu has these options: Status (queue size, last upload result), Reload settings, Send now, Open Android settings, Exit.

## Locking the tablet to the app
Pick one of these:
- **Set as Home app:** press Home, choose *Satisfaction Kiosk* → *Always*. The app then starts on boot and the Home button returns to it.
- **Screen pinning:** turn on *Settings > Security > Pin windows*, then set `pin_screen = true` in the INI.
- **Samsung Knox / MDM kiosk mode** (Intune, Knox Manage, etc.): this is the most locked-down option for a fleet.

## Destinations (`[database] type`)
| type | Notes |
|---|---|
| `http` | **Recommended.** POSTs JSON to `url`. No database credentials are stored on the tablet. |
| `mssql` | SQL Server, via jTDS. Uses `host`, `port`, `database`, `instance`, `username`, `password`, `table`. |
| `mysql` | MySQL/MariaDB. On MySQL 8, the DB user must use `mysql_native_password`. |
| `postgresql` | PostgreSQL. |
| `none` | Store on the tablet only (`responses.csv`). |

Create the table with the script in `sql/`. Every response has a unique `response_id`, and resent batches are de-duplicated, so retries never double-count.

**HTTP payload**
```json
{"responses":[{"response_id":"uuid","rating":4,"created_utc":"2026-11-23 17:31:02",
  "created_local":"2026-11-23 09:31:02","site_id":"HQ-Lobby","device_id":"a1b2c3..."}]}
```
Return any 2xx status to acknowledge. `server/test_receiver.py` is a standard-library test endpoint you can run on a PC.

## Security note
In direct-DB mode, the database password sits in plain text in `kiosk.ini`, and anyone with USB access to the tablet can read it. Give that login **INSERT-only** rights (see the `sql/` scripts), or use `http` mode behind your API.

## Adding features later
- New INI keys can be read anywhere with `cfg.ini.get("section", "key")` / `.bool()` / `.int()`. Unknown keys are ignored, so older APKs tolerate newer INI files.
- New destinations: implement `ResponseSink` in `Sinks.kt` and add a `type` name to `Sinks.create()`.

## Data collected
Only the following: rating (1–5), timestamp, `site_id`, and `device_id` (identifies the tablet, not the person). No camera, location, or personal data.
