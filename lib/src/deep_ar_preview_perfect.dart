import 'dart:io';

import 'package:flutter/material.dart';

import 'deep_ar_controller_perfect.dart';

/// Displays live preview with desired effects.
class DeepArPreviewPerfect extends StatefulWidget {
  const DeepArPreviewPerfect(this.deepArController, {Key? key, this.onViewCreated})
      : super(key: key);
  final DeepArControllerPerfect deepArController;
  final Function? onViewCreated;

  @override
  State<DeepArPreviewPerfect> createState() => _DeepArPreviewPerfectState();
}

class _DeepArPreviewPerfectState extends State<DeepArPreviewPerfect> {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: AspectRatio(
          aspectRatio: (1 / widget.deepArController.aspectRatio),
          child: Platform.isAndroid ? _androidView() : _iOSView()),
    );
  }

  Widget _iOSView() {
    return widget.deepArController.buildPreview(oniOSViewCreated: () {
      widget.onViewCreated?.call();
      setState(() {});
    });
  }

  Widget _androidView() {
    WidgetsBinding.instance
        .addPostFrameCallback((timeStamp) => widget.onViewCreated?.call());
    return widget.deepArController.isInitialized
        ? widget.deepArController.buildPreview()
        : const SizedBox.shrink();
  }
}
