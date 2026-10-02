import 'dart:ui';

/// Normalized [0,1] position of `point` within an aspect-fit image displayed
/// in a `containerSize` box - Dart port of the `normalized()` letterbox math
/// in LaptopControlView.swift, used identically for both the laptop and
/// phone control screens.
Offset normalized(Offset point, Size containerSize, Size imageSize) {
  if (containerSize.width <= 0 ||
      containerSize.height <= 0 ||
      imageSize.width <= 0 ||
      imageSize.height <= 0) {
    return Offset.zero;
  }

  final containerAspect = containerSize.width / containerSize.height;
  final imageAspect = imageSize.width / imageSize.height;
  double displayWidth, displayHeight;
  if (containerAspect > imageAspect) {
    displayHeight = containerSize.height;
    displayWidth = displayHeight * imageAspect;
  } else {
    displayWidth = containerSize.width;
    displayHeight = displayWidth / imageAspect;
  }
  final originX = (containerSize.width - displayWidth) / 2;
  final originY = (containerSize.height - displayHeight) / 2;
  final nx = ((point.dx - originX) / displayWidth).clamp(0.0, 1.0);
  final ny = ((point.dy - originY) / displayHeight).clamp(0.0, 1.0);
  return Offset(nx, ny);
}
