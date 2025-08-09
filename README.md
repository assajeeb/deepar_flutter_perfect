Sure! Here’s your updated README content with all references changed to **deepar\_flutter\_perfect** and polished for clarity:

---

# deepar\_flutter\_perfect

An enhanced Flutter plugin based on the official DeepAR SDK, providing robust support for loading AR effects from assets, file paths, and URLs with automatic caching. This enables loading effects stored locally or directly from the internet, ensuring smooth AR experiences on **all Android devices—including Xiaomi—and iOS**.

This plugin is a fork of the official [DeepAR Flutter SDK](https://pub.dev/packages/deepar_flutter_perfect). Supported platforms: **Android (SDK 23+)** and **iOS (13.0+)**.

---

## Features

* Load AR effects from assets, file paths, and remote URLs with caching ✨ (New!)
* Live AR preview ✅
* Take screenshots ✅
* Record videos ✅
* Flip camera ✅
* Toggle flash ✅

| Platform | Android | iOS       |
| -------- | ------- | --------- |
| Support  | SDK 23+ | iOS 13.0+ |

---

## Breaking Changes

* **v0.1.7**: The `initialize()` method now returns an `InitializeResult` object with `success` and `message` properties (not just a boolean). See [Flutter usage](#flutter) for updated code.
* **v0.1.9**: Updated iOS implementation with improved compatibility and effect loading.
* **v0.1.8**: Improved iOS camera initialization and error handling.

---

## Installation

### Before you begin

Visit the [DeepAR developer site](https://developer.deepar.ai) to create a project and obtain license keys for Android and iOS.

---

### Android

1. Set `compileSdkVersion` to 33 or higher and `minSdkVersion` to 23 or higher.

2. Download native Android dependencies from [downloads](https://developer.deepar.ai/downloads) and place `deepar.aar` into `android/app/libs/`.

3. Run:

   ```bash
   flutter clean
   flutter pub upgrade
   ```

4. Add these permissions to your `AndroidManifest.xml`:

   ```xml
   <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
   <uses-permission android:name="android.permission.RECORD_AUDIO"/>
   <uses-permission android:name="android.permission.CAMERA" />
   <uses-permission android:name="android.permission.INTERNET" />
   ```

5. Add these rules to `proguard-rules.pro` to prevent release crashes:

   ```
   -keepclassmembers class ai.deepar.ar.DeepAR { *; }
   -keepclassmembers class ai.deepar.ar.core.videotexture.VideoTextureAndroidJava { *; }
   -keep class ai.deepar.ar.core.videotexture.VideoTextureAndroidJava
   ```

---

### iOS

1. Set deployment target to iOS 13.0 or later.
2. Run:

   ```bash
   flutter clean
   flutter pub upgrade
   cd ios
   pod install
   ```
3. Add these keys to your `Info.plist` to request camera and microphone permissions:

   ```xml
   <key>NSCameraUsageDescription</key>
   <string>Camera access is required for AR effects</string>
   <key>NSMicrophoneUsageDescription</key>
   <string>Microphone access is required for recording videos</string>
   ```
4. Add this snippet to your `ios/Podfile` for permission handler support:

   ```ruby
   post_install do |installer|
     installer.pods_project.targets.each do |target|
       target.build_configurations.each do |config|
         config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
           '$(inherited)',
           'PERMISSION_CAMERA=1',
           'PERMISSION_MICROPHONE=1',
         ]
       end
     end
   end
   ```

---

## Flutter Usage <a name="flutter"></a>

1. Initialize `DeepArControllerPerfect` by passing your license keys:

```dart
final DeepArControllerPerfect _controller = DeepArControllerPerfect();
final result = await _controller.initialize(
  androidLicenseKey: "---android key---",
  iosLicenseKey: "---iOS key---",
  resolution: Resolution.medium,
);

if (result.success) {
  print("Initialization successful: ${result.message}");

  if (Platform.isIOS) {
    Timer.periodic(Duration(milliseconds: 500), (timer) {
      if (_controller.isInitialized) {
        print('iOS view fully initialized');
        setState(() {});
        timer.cancel();
      } else if (timer.tick > 20) {
        print('Timeout waiting for iOS view initialization');
        timer.cancel();
      }
    });
  }
} else {
  print("Initialization failed: ${result.message}");
}
```

2. Display the preview widget:

```dart
@override
Widget build(BuildContext context) {
  return _controller.isInitialized
      ? DeepArPreviewPerfect(_controller)
      : const Center(child: Text("Loading Preview"));
}
```

3. Load effects from assets, file paths, or URLs:

```dart
await _controller.switchEffect("assets/effects/my_effect.deepar");
await _controller.switchEffect("/path/to/effect/file.deepar");
await _controller.switchEffect("https://example.com/effects/my_effect.deepar");
```

4. Take screenshots:

```dart
final File file = await _controller.takeScreenshot();
```

5. Record videos:

```dart
if (_controller.isRecording) {
  _controller.stopVideoRecording();
} else {
  final File videoFile = _controller.startVideoRecording();
}
```

---

For more details, visit [DeepAR Developer Help](https://help.deepar.ai/en/).

---

