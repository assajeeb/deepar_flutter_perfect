import 'dart:io';

import 'package:flutter/material.dart';

import 'deep_ar_controller_plus.dart';

/// Displays live preview with desired effects.
class DeepArPreviewPlus extends StatefulWidget {
  const DeepArPreviewPlus(this.deepArController, {Key? key, this.onViewCreated})
      : super(key: key);
  final DeepArControllerPlus deepArController;
  final Function? onViewCreated;

  @override
  State<DeepArPreviewPlus> createState() => _DeepArPreviewPlusState();
}

class _DeepArPreviewPlusState extends State<DeepArPreviewPlus> {
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
