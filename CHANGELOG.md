## 0.2.0

- Version bump for new feature development 🚀
  - Preparing for implementation of pause and resume camera methods
  - Improved stability and performance

- Fixed iOS camera reinitialization issue 🛠️
  - Fixed white screen issue when destroying and recreating the controller on iOS
  - Added proper cleanup and delay mechanisms for camera resources
  - Improved camera initialization sequence to ensure proper restart
  - Enhanced logging for better debugging

## 0.1.9

- Updated iOS implementation for new DeepAR SDK 🚀
  - Fixed compatibility issues with the latest DeepAR SDK
  - Updated code to use the new API structure
  - Removed references to deprecated ARView class
  - Improved error handling for effect loading

## 0.1.8

- Fixed iOS camera initialization issues 🛠️
  - Added better error handling and debugging for iOS initialization
  - Improved platform view creation process with proper timing
  - Added fallback dimensions when native dimensions can't be retrieved
  - Enhanced example app with iOS-specific initialization handling

- Fixed iOS effect loading issues 🛠️
  - Improved error handling and debugging for effect loading on iOS
  - Enhanced path resolution for effects from assets, file paths, and URLs
  - Added proper error reporting when effects can't be found or loaded
  - Fixed issues with asset path resolution on iOS

## 0.1.7

- **BREAKING CHANGE**: Changed `initialize()` method to return `InitializeResult` with success status and message 🚨
  - Previously returned only a boolean value
  - Now returns an object with `success` (boolean) and `message` (string) properties
  - Provides more detailed information about initialization results
  - Requires updating code that calls `initialize()` to handle the new return type

## 0.1.6

- Fixed iOS framework linking errors 🛠️
  - Resolved "Undefined symbol" linker errors in iOS builds
  - Fixed framework search paths to properly locate DeepAR.framework

## 0.1.5

- Fixed iOS podspec configuration for DeepAR.framework 🛠️
  - Updated preserve_paths to correctly reference the framework
  - Improved xcconfig settings for better iOS integration

## 0.1.4

- Fixed controller initialization and camera issues ([#2](https://github.com/Ifoegbu1/deepar-flutter-plus/issues/2)) 🛠️
  - Added safeguards for Android BufferQueue abandonment issues
  - Fixed "dequeueBuffer: BufferQueue has been abandoned" errors during quick init/destroy cycles
  - Added protection against race conditions in controller lifecycle
  - Improved resource cleanup to prevent memory leaks
  - Enhanced controller state management for more reliable operation
  - Properly reset controller state during destroy
  - Improved initialization process with better error handling
  - Added safeguards for reinitializing the controller


## 0.1.3

- Added issue tracker URL to package metadata 📋
- Improved package discoverability on pub.dev

## 0.1.2

- Fixed iOS build error: 'deepar_flutter-Swift.h' file not found 🛠️
- Updated module name in podspec to match package name

## 0.1.1

- Fixed Android build issue with newer versions of Android Gradle Plugin 🛠️

## 0.1.0

- Code Refactoring 🔄

## 0.0.9

- Added enhanced iOS support for effect loading 📱
  - Support for loading effects, filters and masks from URLs and file paths

## 0.0.8

- Renamed core classes to include "Plus" suffix for better clarity 🏷️
  - `DeepArController` → `DeepArControllerPlus`
  - `DeepArPreview` → `DeepArPreviewPlus`

## 0.0.7

- Now you can load effects, filters, and masks from:
  - Asset files
  - File paths (e.g., "/path/to/effect/file.deepar")
  - URLs (e.g., "https://example.com/effects/effect.deepar")
- Added automatic caching for downloaded effects

## 0.0.6

- Added support for loading effects from file paths 🎉
- Now you can load effects from:
  - File paths (e.g., "/path/to/effect/file.deepar")
- Perfect for loading downloaded or dynamically stored effects
