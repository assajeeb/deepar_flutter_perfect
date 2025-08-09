package com.deepar.ai;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Build;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.TorchState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.deepar.ar.CameraResolutionPreset;
import ai.deepar.ar.DeepAR;
import ai.deepar.ar.DeepARImageFormat;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class SafeCameraXHandler implements MethodChannel.MethodCallHandler {
    private static final String TAG = "SafeCameraXHandler";
    private static final int NUMBER_OF_BUFFERS = 2;

    private final Activity activity;
    private DeepAR deepAR;
    private final long textureId;
    private ProcessCameraProvider processCameraProvider;
    private ListenableFuture<ProcessCameraProvider> future;
    private ByteBuffer[] buffers;
    private int currentBuffer = 0;
    private final CameraResolutionPreset resolutionPreset;

    private int defaultLensFacing = CameraSelector.LENS_FACING_FRONT;
    private int lensFacing = defaultLensFacing;
    private androidx.camera.core.Camera camera;

    // Safety flags
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private final AtomicBoolean isCameraStarted = new AtomicBoolean(false);

    // Constructor matches call in DeepArPlugin
    public SafeCameraXHandler(Activity activity, long textureId, DeepAR deepAR, CameraResolutionPreset cameraResolutionPreset) {
        this.activity = activity;
        this.textureId = textureId;
        this.deepAR = deepAR;
        this.resolutionPreset = cameraResolutionPreset;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        try {
            switch (call.method) {
                case MethodStrings.startCamera:
                    startNative(result);
                    break;
                case "flip_camera":
                    flipCamera();
                    result.success(true);
                    break;
                case "toggle_flash":
                    boolean isFlash = toggleFlash();
                    result.success(isFlash);
                    break;
                case "destroy":
                    destroy();
                    result.success("SHUTDOWN");
                    break;
                default:
                    result.notImplemented();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in method call: " + call.method, e);
            result.error("ERROR", "Error in " + call.method + ": " + e.getMessage(), null);
        }
    }

    private boolean toggleFlash() {
        try {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                boolean isFlashOn = camera.getCameraInfo().getTorchState().getValue() == TorchState.ON;
                camera.getCameraControl().enableTorch(!isFlashOn);
                return !isFlashOn;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling flash", e);
        }
        return false;
    }

    private void flipCamera() {
        if (isDestroyed.get()) {
            Log.w(TAG, "Trying to flip camera after destruction");
            return;
        }

        lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;

        // Unbind and restart to avoid mirrored/incorrect frames
        try {
            if (future != null && future.isDone()) {
                ProcessCameraProvider cameraProvider = future.get();
                if (cameraProvider != null) {
                    cameraProvider.unbindAll();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error unbinding camera", e);
        }
        isCameraStarted.set(false);
        startNative(null);
    }

    private void startNative(MethodChannel.Result result) {
        if (isDestroyed.get()) {
            Log.w(TAG, "Trying to start camera after destruction");
            if (result != null) result.error("DESTROYED", "Camera handler has been destroyed", null);
            return;
        }

        // If already started, unbind first
        if (isCameraStarted.get()) {
            Log.w(TAG, "Camera already started, stopping first");
            try {
                if (future != null && future.isDone()) {
                    ProcessCameraProvider cameraProvider = future.get();
                    if (cameraProvider != null) cameraProvider.unbindAll();
                }
                isCameraStarted.set(false);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error unbinding camera", e);
            }
        }

        future = ProcessCameraProvider.getInstance(activity);
        Executor executor = ContextCompat.getMainExecutor(activity);

        // Determine target width/height based on orientation & preset
        final int finalWidth;
        final int finalHeight;
        int orientation = getScreenOrientation();
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            finalWidth = resolutionPreset.getWidth();
            finalHeight = resolutionPreset.getHeight();
        } else {
            finalWidth = resolutionPreset.getHeight();
            finalHeight = resolutionPreset.getWidth();
        }

        // Apply Xiaomi / Redmi Note 11 safe fallback if necessary
        if (isXiaomiRedmiNote11()) {
            int width = resolutionPreset.getWidth();
            int height = resolutionPreset.getHeight();

            if (Build.MANUFACTURER.toLowerCase().contains("xiaomi") &&
                    Build.MODEL.toLowerCase().contains("redmi note 11")) {
                // limit resolution to avoid camera crash or weird artifacts on Redmi Note 11
                width = Math.min(width, 1280);
                height = Math.min(height, 720);
                Log.d(TAG, "Applying Redmi Note 11 safe resolution fallback: " + width + "x" + height);
            }
        }

        // Initialize buffers sized for YUV 420 (1.5 * pixels)
        try {
            int bufferSize = finalWidth * finalHeight * 3 / 2; // YUV420 size
            buffers = new ByteBuffer[NUMBER_OF_BUFFERS];
            for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
                buffers[i] = ByteBuffer.allocateDirect(bufferSize);
                buffers[i].order(ByteOrder.nativeOrder());
                buffers[i].position(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error allocating buffers", e);
            if (result != null) result.error("BUFFER_ERROR", "Failed to allocate camera buffers", e.getMessage());
            return;
        }

        future.addListener(() -> {
            if (isDestroyed.get()) {
                Log.w(TAG, "Camera setup callback after destruction");
                if (result != null) result.error("DESTROYED", "Camera handler has been destroyed", null);
                return;
            }

            try {
                processCameraProvider = future.get();
                Size cameraResolution = new Size(finalWidth, finalHeight);

                ImageAnalysis.Analyzer analyzer = new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        if (isDestroyed.get()) {
                            image.close();
                            return;
                        }

                        try {
                            Image img = image.getImage();
                            if (img == null || img.getFormat() != ImageFormat.YUV_420_888) {
                                image.close();
                                return;
                            }

                            int width = img.getWidth();
                            int height = img.getHeight();
                            int chromaWidth = width / 2;
                            int chromaHeight = height / 2;

                            boolean swapUV = needsUVSwap(img);

                            int required = width * height + 2 * (chromaWidth * chromaHeight);
                            if (buffers[currentBuffer].capacity() < required) {
                                Log.e(TAG, "Buffer too small (required=" + required + ", cap=" + buffers[currentBuffer].capacity() + ")");
                                image.close();
                                return;
                            }

                            byte[] byteData = new byte[required];
                            int pos = 0;

                            // Copy Y plane
                            ByteBuffer yPlane = img.getPlanes()[0].getBuffer();
                            int yRowStride = img.getPlanes()[0].getRowStride();
                            int yPixelStride = img.getPlanes()[0].getPixelStride();

                            for (int row = 0; row < height; row++) {
                                int rowStart = row * yRowStride;
                                for (int col = 0; col < width; col++) {
                                    int index = rowStart + col * yPixelStride;
                                    if (index >= yPlane.limit()) {
                                        Log.w(TAG, "Y plane index out of bounds: " + index + " >= " + yPlane.limit());
                                        break;
                                    }
                                    byteData[pos++] = yPlane.get(index);
                                }
                            }

                            // Copy UV planes carefully with stride and bounds check
                            ByteBuffer uPlane = img.getPlanes()[1].getBuffer();
                            int uRowStride = img.getPlanes()[1].getRowStride();
                            int uPixelStride = img.getPlanes()[1].getPixelStride();

                            ByteBuffer vPlane = img.getPlanes()[2].getBuffer();
                            int vRowStride = img.getPlanes()[2].getRowStride();
                            int vPixelStride = img.getPlanes()[2].getPixelStride();

                            for (int row = 0; row < chromaHeight; row++) {
                                int uRowStart = row * uRowStride;
                                int vRowStart = row * vRowStride;

                                for (int col = 0; col < chromaWidth; col++) {
                                    int uIndex = uRowStart + col * uPixelStride;
                                    int vIndex = vRowStart + col * vPixelStride;

                                    byte uValue = 0;
                                    byte vValue = 0;
                                    if (uIndex < uPlane.limit()) {
                                        uValue = uPlane.get(uIndex);
                                    }
                                    if (vIndex < vPlane.limit()) {
                                        vValue = vPlane.get(vIndex);
                                    }

                                    if (swapUV) {
                                        byteData[pos++] = uValue;
                                        byteData[pos++] = vValue;
                                    } else {
                                        byteData[pos++] = vValue;
                                        byteData[pos++] = uValue;
                                    }

                                }
                            }

                            // Put into direct ByteBuffer
                            buffers[currentBuffer].position(0);
                            buffers[currentBuffer].put(byteData);
                            buffers[currentBuffer].position(0);

                            // Send to DeepAR
                            if (deepAR != null && !isDestroyed.get()) {
                                try {
                                    deepAR.receiveFrame(buffers[currentBuffer],
                                            width, height,
                                            image.getImageInfo().getRotationDegrees(),
                                            lensFacing == CameraSelector.LENS_FACING_FRONT,
                                            DeepARImageFormat.YUV_420_888,
                                            img.getPlanes()[1].getPixelStride()
                                    );
                                } catch (Exception e) {
                                    Log.e(TAG, "Error calling deepAR.receiveFrame", e);
                                }
                            }

                            currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS;

                        } catch (Exception e) {
                            Log.e(TAG, "Error in image analysis", e);
                        } finally {
                            image.close();
                        }
                    }
                };

                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(cameraResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                imageAnalysis.setAnalyzer(executor, analyzer);

                processCameraProvider.unbindAll();

                camera = processCameraProvider.bindToLifecycle((LifecycleOwner) activity,
                        cameraSelector, imageAnalysis);

                isCameraStarted.set(true);
                Log.d(TAG, "Camera started successfully with resolution " + cameraResolution.getWidth() + "x" + cameraResolution.getHeight());

                if (result != null) {
                    result.success(textureId);
                }

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                if (result != null) result.error("CAMERA_ERROR", "Failed to start camera", e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error starting camera", e);
                if (result != null) result.error("UNEXPECTED_ERROR", "Unexpected error starting camera", e.getMessage());
            }
        }, executor);
    }

    public void destroy() {
        if (isDestroyed.compareAndSet(false, true)) {
            Log.d(TAG, "Destroying SafeCameraXHandler");
            try {
                if (processCameraProvider != null) {
                    processCameraProvider.unbindAll();
                    isCameraStarted.set(false);
                }

                if (deepAR != null) {
                    deepAR.setAREventListener(null);
                    deepAR.release();
                    deepAR = null;
                }

                if (buffers != null) {
                    for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
                        buffers[i] = null;
                    }
                }
                buffers = null;

                Log.d(TAG, "SafeCameraXHandler destroyed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error during destroy", e);
            }
        } else {
            Log.w(TAG, "SafeCameraXHandler already destroyed");
        }
    }

    private int getScreenOrientation() {
        return activity.getResources().getConfiguration().orientation;
    }

    private boolean isXiaomiRedmiNote11() {
        try {
            return Build.MANUFACTURER != null && Build.MODEL != null &&
                    Build.MANUFACTURER.toLowerCase().contains("xiaomi") &&
                    Build.MODEL.toLowerCase().contains("redmi");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean needsUVSwap(Image img) {
        try {
            if (Build.MANUFACTURER != null && Build.MODEL != null) {
                String m = Build.MANUFACTURER.toLowerCase();
                String model = Build.MODEL.toLowerCase();
                if (m.contains("xiaomi") && model.contains("redmi")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
