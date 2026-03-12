## Overview

This directory contains the Android projects used to integrate Alphamini’s on‑board
microphone and camera with the Social Interaction Cloud (SIC). The official Alphamini
SDK does not expose continuous mic/camera streams to user code, so we provide small
Android apps that:

- run directly on the robot,
- capture audio and video from the Alphamini hardware, and
- stream data over TCP to SIC components running in Termux on the same device.


Repository layout
-----------------

The Android projects are organised as follows:

- `camera_app/`
  - Android app that opens the Alphamini camera via the Android Camera API.
  - Sends JPEG‑compressed preview frames over TCP to the SIC `MiniCameraSensor`
    (listening on `127.0.0.1:6001` by default).
  - Accepts configuration via intent extras:
    - `target_width` / `target_height` (desired preview size in pixels),
    - `scale_factor` (applied on top of the base/target size),
    - `jpeg_quality` (0–100).
  - Listens for a broadcast (`com.example.alphamini.camera.ACTION_STOP`) so the
    SIC camera component can request a clean shutdown of the activity.

- `microphone_app/` (if present)
  - Android app that captures multichannel audio from the Alphamini microphone
    array and streams it over TCP to the SIC `MiniMicrophoneSensor`.
  - Uses the `MicArrayUtils` API described below to access the raw microphone data.

Each app builds to an APK (e.g. `camera_app-debug.apk`) that can be installed on
the Alphamini using Android Studio or `adb`.


Building the apps (Android Studio)
----------------------------------

1. Open this directory (`alphamini_android`) in Android Studio as an existing project.
2. Let Gradle sync and resolve dependencies.
3. Select the desired module (for example `camera_app`) and build:
   - **Build → Make Project** or **Build → Build APK(s)**.
4. The resulting APKs can be found under the module’s `app/build/outputs/apk/`
   directory (for example `camera_app/app/build/outputs/apk/debug/camera_app-debug.apk`).


Installing on Alphamini
-----------------------

You can install the APKs using either Android Studio or `adb`. We recommend a
USB‑based workflow (no network ADB required).

### Using Android Studio

1. Connect Alphamini to your computer via USB.
2. Ensure USB debugging is enabled on the device and accept the RSA fingerprint
   prompt when it appears.
3. In Android Studio, select the Alphamini device in the device chooser.
4. Press **Run** (or use **Run → Run 'app'**) on the `camera_app` module to
   build and install the app directly onto the robot.

### Using `adb` from the command line

1. Install Android platform tools on your development machine (for example, on
   macOS with Homebrew):

   ```bash
   brew install --cask android-platform-tools
   ```

2. Connect Alphamini via USB and verify that `adb` can see it:

   ```bash
   adb devices
   ```

3. Install or update the camera app:

   ```bash
   cd misc/alphamini/alphamini_android/camera_app/app/build/outputs/apk/debug

   # First install
   adb install camera_app-debug.apk

   # Update an existing install
   adb install -r camera_app-debug.apk

   # If the APK is marked test-only, include -t
   adb install -r -t camera_app-debug.apk
   ```

4. Repeat the same process for the microphone app APK.


How SIC uses these apps
-----------------------

- On Alphamini, SIC runs inside Termux and starts device‑side components such as
  `MiniCameraSensor` and `MiniMicrophoneSensor`.
- These components open TCP server sockets on `127.0.0.1` (camera on port 6001
  by default, microphone on its own port).
- The Android apps (running as normal Android apps on the same device) connect
  to those sockets and continuously stream encoded frames/samples.
- SIC decodes the streams and publishes messages into Redis for downstream
  applications (for example, the `demo_alphamini_camera.py` demo).


Microphone array access (MicArrayUtils)
---------------------------------------

The microphone app uses the Alphamini microphone array API to access 4‑channel
raw data and 2‑channel reference signals.

### Change log

| Version | Changes | Date |
|--------|---------|------|
| V1.2   | Added camera component, some reorganizing | 2026.03.12
| V1.1   | Enabled configuration of sample rate, bit depth, and other parameters | 2019.12.4 |
| V1.0   | Initial version | 2019.11.27 |

### How to obtain microphone array access permission

1. **Add metadata**

   In the `AndroidManifest.xml` file, within the `<application>` tag, add:

   ```xml
   <meta-data android:name="ubt-master-app" android:value="third_part_speechservice"/>
   ```

2. **Restart the robot after installation**

   Each time the robot boots up, it checks whether the installed APK contains
   the `third_part_speechservice` tag.

   - If the tag exists, the system will **disable** the built‑in `SpeechService`,
     granting microphone array access to the installed APK.
   - Once the built‑in `SpeechService` is disabled, the user will no longer be
     able to access the Tencent Dingdang voice service.

### Releasing microphone array access

1. Uninstall the app or remove the `<meta-data>` tag.
2. Restart the robot.

After restarting, the system’s built‑in `SpeechService` will regain microphone
array access, allowing the user to continue using Tencent Dingdang voice service.

### API description

**Key class:** `MicArrayUtils`

#### Parameter explanation

```java
/***
 * @param context
 * @param sampleRates Sample rate
 * @param bits        Bit depth
 * @param periodCount Number of samples before triggering data callback
 *                    See {@link DataCallback#onAudioData(byte[])}
 */
```