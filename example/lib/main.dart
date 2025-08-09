import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:deepar_flutter_plus/deepar_flutter_plus.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DeepAR Plus Example',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const ARView(),
    );
  }
}

class ARView extends StatefulWidget {
  const ARView({
    super.key,
  });

  @override
  State<ARView> createState() => _ARViewState();
}

class _ARViewState extends State<ARView> {
  final DeepArControllerPlus _controller = DeepArControllerPlus();
  bool isInitialized = false;
  final String effectURL = 'YOUR_EFFECT_URL_HERE';

  @override
  void initState() {
    _initializeAR();
    super.initState();
  }

  Future<void> _initializeAR() async {
    try {
      // Initialize DeepAR
      final result = await _controller.initialize(
        androidLicenseKey: "<YOUR-ANDROID-LICENSE-KEY>",
        iosLicenseKey: "<YOUR-IOS-LICENSE-KEY>",
        resolution: Resolution.medium,
      );

      log('AR initialization result: ${result.success}, message: ${result.message}');

      if (result.success) {
        _controller.switchEffect(effectURL);

        // For iOS, we need to wait for the platform view to be created
        if (Platform.isIOS) {
          log('iOS platform detected, waiting for view initialization');
          // Check initialization status periodically
          _checkIOSInitialization();
        } else {
          setState(() {
            isInitialized = true;
          });
        }
      } else {
        log('Failed to initialize AR: ${result.message}');
      }
    } catch (e, s) {
      log('Error initializing AR: $e', stackTrace: s);
    }
  }

  // Helper method to check iOS initialization status
  void _checkIOSInitialization() {
    // Start a timer to check initialization status
    Timer.periodic(const Duration(milliseconds: 500), (timer) {
      if (_controller.isInitialized) {
        log('iOS view is now initialized');
        setState(() {
          isInitialized = true;
        });
        timer.cancel();
      } else if (timer.tick > 20) {
        // Timeout after 10 seconds
        log('Timeout waiting for iOS view initialization');
        timer.cancel();
      }
    });
  }

  @override
  void dispose() {
    _controller.destroy();

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return isInitialized
        ? Transform.scale(
            scale: _controller.aspectRatio * 1.3, //change value as needed
            child: DeepArPreviewPlus(_controller),
          )
        : const Center(
            child: CircularProgressIndicator(),
          );
  }
}
