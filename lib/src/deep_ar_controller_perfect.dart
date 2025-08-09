import 'dart:developer';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:vector_math/vector_math_64.dart' as vector;

import 'deep_ar_platform_handler.dart';
import 'platform_strings.dart';
import 'resolution_preset.dart';
import 'utils.dart';

/// Result of the initialize method containing success status and a message
class InitializeResult {
  /// Whether initialization was successful
  final bool success;

  /// Message describing the result of initialization
  final String message;

  /// Creates a new [InitializeResult]
  const InitializeResult({required this.success, required this.message});
}

/// Controls all interaction with DeepAR Sdk.
class DeepArControllerPlus {
  late final DeepArPlatformHandler _deepArPlatformHandler;
  late final Resolution _resolution;

  int? _textureId;
  Size? _imageSize;
  double? _aspectRatio;
  bool _hasPermission = false;
  String? _iosLicenseKey;
  bool _isRecording = false;

  CameraDirection _cameraDirection = CameraDirection.front;
  bool _flashState = false;

  DeepArControllerPlus() {
    _deepArPlatformHandler = DeepArPlatformHandler();
  }

  ///Return true if the camera preview is initialized
  ///
  ///For [iOS], please call the function after [DeepArPreviewPlus] widget has been built.
  bool get isInitialized {
    // Check if textureId is set, which indicates the native resources are initialized
    if (_textureId == null) {
      log("DeepAR not initialized: textureId is null");
      return false;
    }

    // Additional platform-specific checks
    if (Platform.isAndroid) {
      // For Android, having a valid textureId is sufficient
      return true;
    } else if (Platform.isIOS) {
      // For iOS, we need both textureId and imageSize to be set
      // since textureId is set in onPlatformViewCreated callback
      if (_imageSize == null) {
        log("DeepAR iOS not fully initialized: imageSize is null");
        return false;
      }
      if (_aspectRatio == null) {
        log("DeepAR iOS not fully initialized: aspectRatio is null");
        return false;
      }
      return true;
    }

    return false;
  }

  ///If the user has allowed required camera permissions
  bool get hasPermission => _hasPermission;

  ///Aspect ratio of the preview image
  ///
  ///For [iOS], please call the function after [DeepArPreviewPlus] widget has been built.
  double get aspectRatio => _aspectRatio ?? 1.0;

  ///Return true if the recording is in progress.
  bool get isRecording => _isRecording;

  ///Get current flash state as [FlashState.on] or [FlashState.off]
  bool get flashState => _flashState;

  ///Size of the preview image
  ///
  ///For [iOS], please call the function after [DeepArPreviewPlus] widget has been built.
  Size get imageDimensions {
    assert(isInitialized, "DeepArController isn't initialized yet");
    return _imageSize!;
  }

  ///Get current  camera direction as [CameraDirection.front] or [CameraDirection.rear]
  CameraDirection get cameraDirection => _cameraDirection;

  // Track initialization/destruction state to prevent race conditions
  bool _isInitializing = false;
  bool _isDestroying = false;
  DateTime? _lastDestroyTime;

  ///Initializes the DeepAR SDK with license keys and asks for required camera and microphone permissions.
  ///Returns an [InitializeResult] with success status and a message describing the result.
  ///
  ///[androidLicenseKey] and [iosLicenseKey] both cannot be null together.
  ///
  ///Recommended resolution: [Resolution.medium] for optimum quality without performance tradeoffs
  Future<InitializeResult> initialize({
    required String? androidLicenseKey,
    required String? iosLicenseKey,
    Resolution resolution = Resolution.medium,
  }) async {
    assert(androidLicenseKey != null || iosLicenseKey != null,
        "Both android and iOS license keys cannot be null");

    // Prevent concurrent initialization
    if (_isInitializing) {
      log("DeepAR initialization already in progress. Skipping.");
      return const InitializeResult(
          success: false, message: "Initialization already in progress");
    }

    // Set initializing flag
    _isInitializing = true;

    try {
      // If already initialized, destroy first to ensure clean state
      if (_textureId != null) {
        log("DeepAR controller is already initialized. Destroying previous instance before re-initializing.");
        await destroy();
      }

      // Check if we need to wait after a recent destroy operation
      if (_lastDestroyTime != null) {
        final timeSinceDestroy = DateTime.now().difference(_lastDestroyTime!);
        const minimumWaitTime = Duration(milliseconds: 500);

        if (timeSinceDestroy < minimumWaitTime) {
          // Need to wait longer after destroy
          final waitTime = minimumWaitTime - timeSinceDestroy;
          log("Waiting ${waitTime.inMilliseconds}ms after destroy before reinitializing");
          await Future.delayed(waitTime);
        }
      }

      // Reset state to ensure clean initialization
      _resetState();

      _iosLicenseKey = iosLicenseKey;
      _resolution = resolution;
      _hasPermission = await _askMediaPermission();

      if (!_hasPermission) {
        log("Camera or microphone permission denied");
        return const InitializeResult(
            success: false, message: "Camera or microphone permission denied");
      }

      if (Platform.isAndroid) {
        assert(androidLicenseKey != null, "androidLicenseKey missing");
        log("Initializing DeepAR on Android");

        // Try initialization with retries for Android
        String? dimensions;
        int retryCount = 0;
        const maxRetries = 2;
        String errorMessage = "";

        while (dimensions == null && retryCount <= maxRetries) {
          try {
            dimensions = await _deepArPlatformHandler.initialize(
                androidLicenseKey!, resolution);
          } catch (e) {
            errorMessage = "Error during Android initialization: $e";
            log("Error during Android initialization attempt ${retryCount + 1}: $e");
            if (retryCount < maxRetries) {
              // Wait before retry
              await Future.delayed(const Duration(milliseconds: 300));
            }
          }
          retryCount++;
        }

        if (dimensions != null) {
          _imageSize = sizeFromEncodedString(dimensions);
          _aspectRatio = _imageSize!.width / _imageSize!.height;

          // Start camera with retry mechanism
          int cameraRetryCount = 0;
          const maxCameraRetries = 2;
          bool cameraStarted = false;
          String cameraErrorMessage = "";

          while (!cameraStarted && cameraRetryCount <= maxCameraRetries) {
            try {
              _textureId = await _deepArPlatformHandler.startCameraAndroid();
              cameraStarted = true;
              log("DeepAR initialized successfully on Android with textureId: $_textureId");
            } catch (e) {
              cameraErrorMessage = "Error starting camera: $e";
              log("Error starting camera on attempt ${cameraRetryCount + 1}: $e");
              if (cameraRetryCount < maxCameraRetries) {
                await Future.delayed(const Duration(milliseconds: 300));
              }
            }
            cameraRetryCount++;
          }

          if (cameraStarted) {
            return const InitializeResult(
                success: true,
                message: "DeepAR initialized successfully on Android");
          } else {
            return InitializeResult(
                success: false,
                message: cameraErrorMessage.isNotEmpty
                    ? cameraErrorMessage
                    : "Failed to start camera after multiple attempts");
          }
        } else {
          log("Failed to get dimensions from DeepAR initialization after retries");
          return InitializeResult(
              success: false,
              message: errorMessage.isNotEmpty
                  ? errorMessage
                  : "Failed to get dimensions from DeepAR initialization after retries");
        }
      } else if (Platform.isIOS) {
        assert(iosLicenseKey != null, "iosLicenseKey missing");
        log("Initializing DeepAR on iOS");
        _imageSize = iOSImageSizeFromResolution(resolution);
        _aspectRatio = _imageSize!.width / _imageSize!.height;

        // Note: On iOS, _textureId is set later in buildPreview's onPlatformViewCreated
        log("DeepAR partially initialized on iOS. Will complete when view is created.");
        return const InitializeResult(
            success: true,
            message:
                "DeepAR partially initialized on iOS. Will complete when view is created.");
      } else {
        throw ("Platform not supported");
      }
    } catch (e) {
      log("Error during DeepAR initialization: $e");
      _resetState();
      return InitializeResult(
          success: false, message: "Error during DeepAR initialization: $e");
    } finally {
      _isInitializing = false;
    }
  }

  ///Builds and returns the DeepAR Camera Preview.
  ///
  ///[oniOSViewCreated] callback to update [imageDimensions] and [aspectRatio] after iOS
  ///widget is built
  ///
  ///Not recommended to use directly. Please use the wrapper [DeepArPreviewPlus] instead.
  ///
  ///Android layer uses FlutterTexture while iOS uses NativeViews.
  ///See: https://api.flutter.dev/flutter/widgets/Texture-class.html
  ///https://docs.flutter.dev/development/platform-integration/ios/platform-views
  Widget buildPreview({Function? oniOSViewCreated}) {
    try {
      if (Platform.isAndroid) {
        if (_textureId == null) {
          log("Error: Attempting to build preview with null textureId on Android");
          return const SizedBox
              .shrink(); // Return empty widget instead of crashing
        }
        return Texture(textureId: _textureId!);
      } else if (Platform.isIOS) {
        if (_iosLicenseKey == null) {
          log("Error: Attempting to build iOS preview with null license key");
          return const SizedBox.shrink();
        }

        return UiKitView(
            viewType: "deep_ar_view",
            layoutDirection: TextDirection.ltr,
            creationParams: <String, dynamic>{
              PlatformStrings.licenseKey: _iosLicenseKey,
              PlatformStrings.resolution: _resolution.stringValue
            },
            creationParamsCodec: const StandardMessageCodec(),
            onPlatformViewCreated: ((id) {
              log("iOS platform view created with id: $id");
              _textureId = id;

              // Set up native listener first to ensure we don't miss any callbacks
              _setNativeListenerIos();

              // Add a small delay to ensure the view is fully created before getting dimensions
              Future.delayed(const Duration(milliseconds: 100), () {
                _deepArPlatformHandler
                    .getResolutionDimensions(_textureId!)
                    .then((value) {
                  if (value != null) {
                    _imageSize = sizeFromEncodedString(value);
                    _aspectRatio = _imageSize!.width / _imageSize!.height;
                    log("iOS view dimensions set: $_imageSize, aspect ratio: $_aspectRatio");
                  } else {
                    log("Warning: Failed to get iOS view dimensions");
                    // Fallback to default dimensions if we can't get them from the platform
                    _imageSize = iOSImageSizeFromResolution(_resolution);
                    _aspectRatio = _imageSize!.width / _imageSize!.height;
                    log("Using fallback dimensions: $_imageSize, aspect ratio: $_aspectRatio");
                  }

                  // Notify that iOS view is created and initialized
                  oniOSViewCreated?.call();
                }).catchError((error) {
                  log("Error getting iOS view dimensions: $error");
                  // Fallback to default dimensions on error
                  _imageSize = iOSImageSizeFromResolution(_resolution);
                  _aspectRatio = _imageSize!.width / _imageSize!.height;
                  log("Using fallback dimensions after error: $_imageSize, aspect ratio: $_aspectRatio");
                  oniOSViewCreated?.call();
                });
              });
            }));
      } else {
        log("Platform not supported for DeepAR");
        return const SizedBox.shrink();
      }
    } catch (e) {
      log("Error building DeepAR preview: $e");
      return const SizedBox.shrink();
    }
  }

  ///Starts recording video
  Future<void> startVideoRecording() async {
    if (_isRecording) throw ("Recording already in progress");
    if (Platform.isAndroid) {
      _deepArPlatformHandler.startRecordingVideoAndroid();
      _isRecording = true;
    } else {
      _deepArPlatformHandler.startRecordingVideoIos(_textureId!);
      _isRecording = true;
    }
  }

  ///Stop recording video
  Future<File> stopVideoRecording() async {
    if (!_isRecording) {
      throw ("Invalid stopVideoRecording trigger. No recording was in progress");
    }
    final _file = await platformRun(
        androidFunction: _deepArPlatformHandler.stopRecordingVideoAndroid,
        iOSFunction: () =>
            _deepArPlatformHandler.stopRecordingVideoIos(_textureId!));
    _isRecording = false;
    if (_file == "ENDED_WITH_ERROR") throw ("Video capture failed");

    return File(_file!);
  }

  ///Takes picture of the current frame and returns a [File]
  Future<File> takeScreenshot() async {
    final _file = await platformRun(
        androidFunction: _deepArPlatformHandler.takeScreenShot,
        iOSFunction: () =>
            _deepArPlatformHandler.takeScreenShotIos(_textureId!));
    if (_file == "ENDED_WITH_ERROR") throw ("Screenshot capture failed");

    return File(_file!);
  }

  /// Helper function to handle file caching and path resolution
  /// Returns the file path for the effect, either from cache or local assets
  Future<String> _resolveEffectPath(String path, String effectType) async {
    log('Resolving $effectType path: $path');

    // Check if it's an absolute file path
    if (path.startsWith('/')) {
      final file = File(path);
      if (await file.exists()) {
        log('Using existing file path: $path');
        return path;
      } else {
        log('File does not exist at path: $path');
      }
    }

    // Try to parse as URL
    try {
      final uri = Uri.parse(path);
      if (uri.isAbsolute && (uri.scheme == 'http' || uri.scheme == 'https')) {
        log('Downloading from URL: $path');
        try {
          final file = await DefaultCacheManager().getSingleFile(path);
          log('Downloaded and cached at: ${file.path}');
          return file.path;
        } catch (e) {
          log('Failed to download from URL: $e');
          // Fall through to asset path
        }
      }
    } catch (e) {
      log('Not a valid URL, treating as asset path: $e');
    }

    // If we get here, treat as asset path
    log('Using as asset path: $path');
    return path;
  }

  ///Switch DeepAR with the passed [effect] path from assets, file paths or URL
  Future<String?> switchEffect(String effect) async {
    final effectPath = await _resolveEffectPath(effect, 'Effect').then((path) {
      log('Resolved effect path: $path');
      return path;
    });
    return platformRun(
        androidFunction: () =>
            _deepArPlatformHandler.switchEffectAndroid(effectPath),
        iOSFunction: () =>
            _deepArPlatformHandler.switchCameraIos(effectPath, _textureId!));
  }

  ///Load contents of a DeepAR Studio file as an effect/filter in the scene
  Future<void> switchEffectWithSlot(
      {required String slot,
      required String path,
      String? targetGameObject,
      int? face}) async {
    await platformRun(
        androidFunction: () =>
            _deepArPlatformHandler.switchEffectWithSlot(slot: slot, path: path),
        iOSFunction: () => _deepArPlatformHandler
            .switchEffectWithSlotIos(_textureId!, slot: slot, path: path));
  }

  ///Switch DeepAR with the passed [mask] path from assets, file path or URL
  Future<String?> switchFaceMask(String mask) async {
    final maskPath = await _resolveEffectPath(mask, 'Face Mask');
    return platformRun(
        androidFunction: () =>
            _deepArPlatformHandler.switchFaceMaskAndroid(maskPath),
        iOSFunction: () =>
            _deepArPlatformHandler.switchFaceMaskIos(maskPath, _textureId!));
  }

  ///Switch DeepAR with the passed [filter] path from assets, file path or URL
  Future<String?> switchFilter(String filter) async {
    final filterPath = await _resolveEffectPath(filter, 'Filter');
    return platformRun(
        androidFunction: () =>
            _deepArPlatformHandler.switchFilterAndroid(filterPath),
        iOSFunction: () =>
            _deepArPlatformHandler.switchFilterIos(filterPath, _textureId!));
  }

  ///Moves the selected game object from its current position in a tree and sets it as a direct child of a target game object.
  Future<void> moveGameObject(
      {required String selectedGameObjectName,
      required String targetGameObjectName}) async {
    await platformRun(
        androidFunction: () => _deepArPlatformHandler.moveGameObject(
            selectedGameObjectName, targetGameObjectName),
        iOSFunction: () => _deepArPlatformHandler.moveGameObjectIos(
            _textureId!, selectedGameObjectName, targetGameObjectName));
  }

  ///Changes a node or component newParameter.
  ///
  /// Only allowed datatype for newParameter are :
  /// String, Bool, Vector3, Vector4, Float
  Future<void> changeParameter({
    required String gameObject,
    required String component,
    required String parameter,
    dynamic newParameter,
  }) async {
    if (newParameter is String ||
        newParameter is bool ||
        newParameter is vector.Vector3 ||
        newParameter is vector.Vector4 ||
        newParameter is double) {
      Map<String, dynamic> arguments = {};
      arguments['gameObject'] = gameObject;
      arguments['component'] = component;
      arguments['parameter'] = parameter;

      if (newParameter is vector.Vector3) {
        arguments['x'] = newParameter.x;
        arguments['y'] = newParameter.y;
        arguments['z'] = newParameter.z;
      } else if (newParameter is vector.Vector4) {
        arguments['x'] = newParameter.x;
        arguments['y'] = newParameter.y;
        arguments['z'] = newParameter.z;
        arguments['w'] = newParameter.w;
      } else {
        arguments['newParameter'] = newParameter;
      }

      await platformRun(
          androidFunction: () =>
              _deepArPlatformHandler.changeParameter(arguments),
          iOSFunction: () => _deepArPlatformHandler.changeParameterIos(
              _textureId!, arguments));
    } else {
      log("Invalid datatype passed in newParameter");
      throw ("Invalid field newParameter. Please refer docs to pass correct value.");
    }
  }

  ///Flips Camera and return the current direction
  Future<CameraDirection> flipCamera() async {
    final result = await platformRun(
        androidFunction: _deepArPlatformHandler.flipCamera,
        iOSFunction: () => _deepArPlatformHandler.flipCameraIos(_textureId!));
    if (result != null && result) {
      _cameraDirection = _cameraDirection == CameraDirection.front
          ? CameraDirection.rear
          : CameraDirection.front;
      if (_cameraDirection == CameraDirection.front) _flashState = false;
    }
    return _cameraDirection;
  }

  ///Toggles flash and returns its status
  Future<bool> toggleFlash() async {
    bool result = await platformRun(
        androidFunction: _deepArPlatformHandler.toggleFlash,
        iOSFunction: () => _deepArPlatformHandler.toggleFlashIos(_textureId!));
    _flashState = result;
    return _flashState;
  }

  ///Fire named trigger of an fbx animation set on the currently loaded effect.
  Future<void> fireTrigger({required String trigger}) async {
    await platformRun(
        androidFunction: () => _deepArPlatformHandler.fireTrigger(trigger),
        iOSFunction: () =>
            _deepArPlatformHandler.fireTriggerIos(_textureId!, trigger));
  }

  ///Display debugging stats on screen.
  Future<void> showStats({required bool enabled}) async {
    await platformRun(
        androidFunction: () => _deepArPlatformHandler.showStats(enabled),
        iOSFunction: () =>
            _deepArPlatformHandler.showStatsIos(_textureId!, enabled));
  }

  ///Enable or disable global physics simulation.
  Future<void> simulatePhysics({required bool enabled}) async {
    await platformRun(
        androidFunction: () => _deepArPlatformHandler.simulatePhysics(enabled),
        iOSFunction: () =>
            _deepArPlatformHandler.simulatePhysicsIos(_textureId!, enabled));
  }

  ///Display physics colliders preview on screen.
  Future<void> showColliders({required bool enabled}) async {
    await platformRun(
        androidFunction: () => _deepArPlatformHandler.showColliders(enabled),
        iOSFunction: () =>
            _deepArPlatformHandler.showCollidersIos(_textureId!, enabled));
  }

  ///Releases all resources required by DeepAR.
  Future<void> destroy() async {
    // Prevent concurrent destroy operations
    if (_isDestroying) {
      log("DeepAR destroy already in progress. Skipping.");
      return;
    }

    if (_textureId == null) {
      log("DeepAR controller is already destroyed or not initialized");
      return;
    }

    _isDestroying = true;

    try {
      log("Destroying DeepAR controller with textureId: $_textureId");
      await platformRun(
          androidFunction: _deepArPlatformHandler.destroy,
          iOSFunction: () => _deepArPlatformHandler.destroyIos(_textureId!));
      log("DeepAR controller destroyed successfully");
    } catch (e) {
      log("Error during DeepAR destroy: $e");
    } finally {
      // Reset controller state regardless of success/failure
      _resetState();
      // Record the time of destruction for initialization cooldown
      _lastDestroyTime = DateTime.now();
      _isDestroying = false;
    }
  }

  /// Resets the internal state of the controller
  void _resetState() {
    _textureId = null;
    _isRecording = false;
    _flashState = false;
    _cameraDirection = CameraDirection.front;
    // Don't reset _imageSize and _aspectRatio as they depend on resolution
    // which doesn't change between initializations
  }

  ///Listen to native delegate methods
  void _setNativeListenerIos() {
    try {
      _deepArPlatformHandler.setListenerIos(_textureId!);
    } catch (e) {
      log("Exception while setting iOS response listener, won't be able to notify flutter once files are available");
      log("Error $e");
    }
  }

  Future<bool> _askMediaPermission() async {
    await [
      Permission.camera,
      Permission.microphone,
    ].request();

    if (await Permission.camera.isGranted &&
        await Permission.microphone.isGranted) {
      return true;
    }

    return false;
  }
}
