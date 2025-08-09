package com.deepar.ai;



import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.MediaScannerConnection;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import android.graphics.BitmapFactory;

import ai.deepar.ar.ARErrorType;
import ai.deepar.ar.AREventListener;
import ai.deepar.ar.CameraResolutionPreset;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.TextureRegistry;
import ai.deepar.ar.DeepAR;
import android.os.Build;

/**
 * DeepArPlugin
 */
public class DeepArPlugin implements FlutterPlugin, AREventListener, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel cameraXChannel, channel;

    private final String TAG = "DEEPAR_LOGS";
    private Activity activity;
    private DeepAR deepAR;
    private Surface surface;
    private long textureId;
    private FlutterPluginBinding flutterPlugin;
    private SurfaceTexture tempSurfaceTexture;
    private String videoFilePath;
    private String screenshotPath;

    private CameraResolutionPreset resolutionPreset;

    private enum DeepArResponse {
        videoStarted,
        videoCompleted,
        videoError,
        screenshotTaken
    }


    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {

        onActivityAttached(binding);
    }


    private void onActivityAttached(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        setDeepArMethodChannel();
        binding.addRequestPermissionsResultListener(this);
    }

    private void setDeepArMethodChannel() {
        channel = new MethodChannel(flutterPlugin.getBinaryMessenger(), MethodStrings.generalChannel);
        channel.setMethodCallHandler(new MethodCallHandler() {
            @Override
            public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
                handleMethods(call, result);
            }
        });
    }

    private void handleMethods(MethodCall call, Result result) {
        Map<String, Object> arguments = (Map<String, Object>) call.arguments;
        boolean enabled = false;

        switch (call.method) {
            case MethodStrings.initialize: // Initialize
                String licenseKey = (String) arguments.get(MethodStrings.licenseKey);
                String resolution = (String) arguments.get(MethodStrings.resolution);

                if(resolution.equals("veryHigh"))
                    resolutionPreset = CameraResolutionPreset.P1920x1080;
                 else if(resolution.equals("high"))
                    resolutionPreset = CameraResolutionPreset.P1280x720;
                 else if(resolution.equals("medium"))
                    resolutionPreset = CameraResolutionPreset.P640x480;
                 else
                    resolutionPreset = CameraResolutionPreset.P640x360;

                Log.d(TAG, "licenseKey = " + licenseKey);
                final boolean success = initializeDeepAR(licenseKey, resolutionPreset);
                if (success) {
                    setCameraXChannel(resolutionPreset);
                }
                result.success("" + resolutionPreset.getWidth() + " " + resolutionPreset.getHeight());
                break;

            case MethodStrings.switchEffect: // Switch Effect
                String effect = ((String) arguments.get("effect"));
                try {
                    InputStream inputStream = _getAssetFileInputStream(effect);
                    deepAR.switchEffect("effect", inputStream);
                    inputStream.close();
                    result.success("switchEffect called successfully");
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error("111", "switchEffect failed", e.getMessage());
                }
                break;

            case MethodStrings.startRecordingVideo:
                try {
                    File file = File.createTempFile("deepar_", ".mp4");
                    videoFilePath = file.getPath();
                    deepAR.startVideoRecording(videoFilePath);
                    result.success("Video recording started");

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("DeepAR", "Error : Unable to create file");
                    videoResult(DeepArResponse.videoError, "Exception while creating file");
                    result.error("111", "Video recording failed", e.getMessage());
                }

                break;

            case MethodStrings.stopRecordingVideo:
                 deepAR.stopVideoRecording();
                 result.success("STOPPING_RECORDING");
                break;
            case "take_screenshot":
                deepAR.takeScreenshot();
                result.success("SCREENSHOT_TRIGGERED");
                break;

            case "switch_face_mask":
                String mask = ((String) arguments.get("effect"));
                if (mask == null || mask.equals("null")) {
                    deepAR.switchEffect("mask", "null");
                    result.success("switchMask called & reset success");
                    return;
                }
                try {
                    InputStream inputStream = _getAssetFileInputStream(mask);
                    deepAR.switchEffect("mask", inputStream);
                    inputStream.close();
                    result.success("switchMask called successfully");
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error("111", "switchMask failed", e.getMessage());
                }

                break;

            case "switch_filter":
                String filter = ((String) arguments.get("effect"));
                if (filter == null || filter.equals("null")){
                    deepAR.switchEffect("filters", "null");
                    result.success("switchFilter called & reset success");
                    return;
                }
                try {
                    InputStream inputStream = _getAssetFileInputStream(filter);
                    deepAR.switchEffect("filters", inputStream);
                    inputStream.close();
                    result.success("switchFilter called successfully");
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error("111", "switchFilter failed", e.getMessage());
                }
                break;
            case "switchEffectWithSlot":
                String slot = ((String) arguments.get("slot"));
                String path = ((String) arguments.get("path"));
                int face = 0;
                String targetGameObject = "";

                try {
                    face = (int) arguments.get("face");
                }catch (Exception e){
                    e.printStackTrace();
                }

                try {
                    targetGameObject = ((String) arguments.get("targetGameObject"));
                }catch (Exception e){
                    e.printStackTrace();
                }

                try {

                    if (path != null && path.toLowerCase().endsWith("none") ){
                        deepAR.switchEffect(slot, getResetPath()); // reset applied effect
                        result.success("switchEffectWithSlot reset success");
                        return;
                    }

                    InputStream inputStream = _getAssetFileInputStream(path);
                    if (targetGameObject != null && !targetGameObject.isEmpty()){
                        deepAR.switchEffect(slot, inputStream, face, targetGameObject);
                    }else{
                        deepAR.switchEffect(slot, inputStream, face);
                    }
                    inputStream.close();
                    result.success("switchEffectWithSlot called successfully");
                } catch (IOException e) {
                    e.printStackTrace();
                    result.error("111", "switchEffectWithSlot failed", e.getMessage());
                }
                break;
            case "fireTrigger":
                String trigger = ((String) arguments.get("trigger"));
                deepAR.fireTrigger(trigger);
                result.success("fireTrigger called successfully");
                break;
            case "showStats":
                enabled = ((boolean) arguments.get("enabled"));
                deepAR.showStats(enabled);
                result.success("showStats called successfully");
                break;
            case "simulatePhysics":
                enabled = ((boolean) arguments.get("enabled"));
                deepAR.simulatePhysics(enabled);
                result.success("simulatePhysics called successfully");
                break;
            case "showColliders":
                enabled = ((boolean) arguments.get("enabled"));
                deepAR.showColliders(enabled);
                result.success("showColliders called successfully");
                break;
            case "moveGameObject":
                String selectedGameObjectName = ((String) arguments.get("selectedGameObjectName"));
                String targetGameObjectName = ((String) arguments.get("targetGameObjectName"));
                deepAR.moveGameObject(selectedGameObjectName, targetGameObjectName);
                result.success("moveGameObject called successfully");
                break;

            case "changeParameter":
                String gameObject = ((String) arguments.get("gameObject"));
                String component = ((String) arguments.get("component"));
                String parameter = ((String) arguments.get("parameter"));
                Object newParameter = arguments.get("newParameter");

                if (newParameter == null){

                    float x = ((Double) arguments.get("x")).floatValue();
                    float y = ((Double) arguments.get("y")).floatValue();
                    float z = ((Double) arguments.get("z")).floatValue();
                    Object w = arguments.get("w");

                    if (w == null){
                        deepAR.changeParameterVec3(gameObject, component, parameter, x, y, z);
                        result.success("changeParameter called successfully");
                    }else{
                        float floatValueW = ((Double) w).floatValue();
                        deepAR.changeParameterVec4(gameObject, component, parameter, x, y, z, floatValueW);
                        result.success("changeParameter called successfully");
                    }
                }
                else if (newParameter instanceof Boolean){
                    deepAR.changeParameterBool(gameObject, component, parameter, (Boolean) newParameter);
                    result.success("changeParameter called successfully");
                }
                else if (newParameter instanceof Double){
                    deepAR.changeParameterFloat(gameObject, component, parameter, ((Double) newParameter).floatValue());
                    result.success("changeParameter called successfully");
                }
                else if (newParameter instanceof String) {
                    try {
                        InputStream inputStream = _getAssetFileInputStream((String) newParameter);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        deepAR.changeParameterTexture(gameObject, component, parameter, bitmap);
                        inputStream.close();
                        result.success("changeParameter called successfully");
                    } catch (IOException e) {
                        e.printStackTrace();
                        result.error("111", "changeParameter failed", e.getMessage());
                    }
                }
                break;
        }


    }

    private String getResetPath(){
        return null;
    }

    private void setCameraXChannel(CameraResolutionPreset resolutionPreset) {
        cameraXChannel = new MethodChannel(flutterPlugin.getBinaryMessenger(), MethodStrings.cameraXChannel);
        // Use the new SafeCameraXHandler instead of CameraXHandler
        final SafeCameraXHandler handler = new SafeCameraXHandler(activity,
                textureId, deepAR, resolutionPreset);
        cameraXChannel.setMethodCallHandler(handler);
        Log.d(TAG, "Using SafeCameraXHandler for better stability");
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onActivityAttached(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        flutterPlugin = null;
    }


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        flutterPlugin = flutterPluginBinding;
    }


    private boolean initializeDeepAR(String licenseKey, CameraResolutionPreset resolutionPreset) {
        try {
            // 1️⃣ Clean up previous DeepAR & resources
            if (deepAR != null) {
                deepAR.setAREventListener(null);
                deepAR.release();
                deepAR = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            if (tempSurfaceTexture != null) {
                tempSurfaceTexture.release();
                tempSurfaceTexture = null;
            }

            // 2️⃣ Xiaomi-specific handling BEFORE creating texture
            if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
                Log.d(TAG, "Applying Xiaomi-specific optimizations");

                // Redmi Note 11 (model 'spes') fallback
                if (Build.MODEL.equalsIgnoreCase("spes")) {
                    resolutionPreset = CameraResolutionPreset.P1280x720;
                    Log.d(TAG, "Using 1280x720 resolution for Redmi Note 11");
                }

                // Force Camera2 API usage
                System.setProperty("persist.camera.HAL3.enabled", "1");
            }

            // 3️⃣ Create SurfaceTexture after deciding resolution
            int width = resolutionPreset.getHeight();
            int height = resolutionPreset.getWidth();

            TextureRegistry.SurfaceTextureEntry entry =
                    flutterPlugin.getTextureRegistry().createSurfaceTexture();
            tempSurfaceTexture = entry.surfaceTexture();
            tempSurfaceTexture.setDefaultBufferSize(width, height);
            surface = new Surface(tempSurfaceTexture);
            textureId = entry.id();

            // 4️⃣ Initialize DeepAR
            deepAR = new DeepAR(activity);
            deepAR.setLicenseKey(licenseKey);
            deepAR.initialize(activity, this);
            deepAR.changeLiveMode(true);
            deepAR.setRenderSurface(surface, width, height);

            // 5️⃣ Start camera with slight delay for Xiaomi devices
            if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
                final CameraResolutionPreset finalPreset = resolutionPreset;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {

                    setCameraXChannel(finalPreset);
                }, 200);
            } else {
                setCameraXChannel(resolutionPreset);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing DeepAR", e);
            return false;
        }
    }



    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        cameraXChannel.setMethodCallHandler(null);
    }

    @Override
    public void screenshotTaken(Bitmap bitmap) {
        CharSequence now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", new Date());
        try {
            //File imageFile = new File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "image_" + now + ".jpg");

            // TODO: 15/07/22 replace with correct path
            //File imageFile = new File("/storage/emulated/0/Download", "image_" + now + ".jpg");
            File imageFile = File.createTempFile("image_" + now, ".jpg");
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
            //MediaScannerConnection.scanFile(activity, new String[]{imageFile.toString()}, null, null);
            screenshotPath = imageFile.getPath();
            screenshotResult(DeepArResponse.screenshotTaken, "screenshot taken");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void videoRecordingStarted() {
        Log.d(TAG, "videoRecordingStarted: "+videoFilePath);
        videoResult(DeepArResponse.videoStarted, "video success");
    }

    @Override
    public void videoRecordingFinished() {
        Log.d(TAG, "videoRecordingFinished: "+videoFilePath);
        videoResult(DeepArResponse.videoCompleted, "video success");
    }

    @Override
    public void videoRecordingFailed() {
        Log.d(TAG, "videoRecordingFailed: "+videoFilePath);
        videoResult(DeepArResponse.videoError, "video failed");
    }

    @Override
    public void videoRecordingPrepared() {

    }

    @Override
    public void shutdownFinished() {

    }

    @Override
    public void initialized() {
        Log.d(TAG, "initialized : DEEPAR");
    }

    @Override
    public void faceVisibilityChanged(boolean b) {

    }

    @Override
    public void imageVisibilityChanged(String s, boolean b) {

    }

    @Override
    public void frameAvailable(Image image) {
    }

    @Override
    public void error(ARErrorType arErrorType, String s) {

    }

    @Override
    public void effectSwitched(String s) {

    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: "+requestCode);
        return false;
    }

    private void videoResult(DeepArResponse callerResponse, String message){
        Map<String, Object> map= new HashMap<String, Object>();
        map.put("caller", callerResponse.name());
        map.put("message", message);
        if (callerResponse == DeepArResponse.videoCompleted){
            map.put("file_path", videoFilePath);
            videoFilePath = "";
        }
        channel.invokeMethod("on_video_result", map);
    }

    private void screenshotResult(DeepArResponse callerResponse, String message){
        Map<String, Object> map= new HashMap<String, Object>();
        map.put("caller", callerResponse.name());
        map.put("message", message);
        if (callerResponse == DeepArResponse.screenshotTaken){
            map.put("file_path", screenshotPath);
            screenshotPath = "";
        }
        channel.invokeMethod("on_screenshot_result", map);
    }

    /// Handle both asset paths and file paths
    InputStream _getAssetFileInputStream(String path) throws IOException {
        if (path == null) {
            throw new IOException("Path cannot be null");
        }

        // Check if the path is a file path
        File file = new File(path);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        // Fall back to asset path handling
        try {
            String assetPath = flutterPlugin
                    .getFlutterAssets()
                    .getAssetFilePathBySubpath(path);
            return flutterPlugin.getApplicationContext().getAssets().open(assetPath);
        } catch (IOException e) {
            throw new IOException("Failed to load file from path: " + path, e);
        }
    }
}
