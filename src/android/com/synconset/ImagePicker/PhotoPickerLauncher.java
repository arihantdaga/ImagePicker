package com.synconset;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.ComponentActivity;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PhotoPickerLauncher {
    private static final String TAG = "PhotoPickerLauncher";
    
    private CordovaInterface cordova;
    private CordovaPlugin plugin;
    private CallbackContext callbackContext;
    private JSONObject options;
    
    // Activity result launchers
    private ActivityResultLauncher<PickVisualMediaRequest> singlePickerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> multiplePickerLauncher;
    private static final int MAX_SELECTION_COUNT = 100; // Maximum images that can be selected
    private boolean launchersInitialized = false;
    private boolean initializationAttempted = false;
    
    public PhotoPickerLauncher(CordovaInterface cordova, CordovaPlugin plugin) {
        this.cordova = cordova;
        this.plugin = plugin;
        // Don't initialize launchers in constructor to avoid lifecycle issues
        // They will be initialized lazily when first needed
    }
    
    /**
     * Check if Photo Picker is available on this device
     */
    public static boolean isPhotoPickerAvailable() {
        // Photo Picker is available natively on Android 13+
        // Todo: It This is available on Android 11, 12 as welll, it is just not available for Adnroid go for 11 and 12. 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        
        // For older versions, check if backport is available
        // ActivityX 1.7.0+ provides this check
        try {
            return ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable();
        } catch (Exception e) {
            Log.e(TAG, "Error checking Photo Picker availability", e);
            return false;
        }
    }
    
    /**
     * Initialize the activity result launchers
     * Returns true if successful, false otherwise
     */
    private boolean initializeLaunchers() {
        // Only attempt once
        if (initializationAttempted) {
            return launchersInitialized;
        }
        
        initializationAttempted = true;
        
        Activity activity = cordova.getActivity();
        if (!(activity instanceof ComponentActivity)) {
            Log.w(TAG, "Activity is not ComponentActivity, cannot use Photo Picker");
            return false;
        }
        
        ComponentActivity componentActivity = (ComponentActivity) activity;
        
        try {
            // Check if we can still register (activity must not be started)
            // This is a workaround - we try to register and catch the exception if too late
            
            // Single image picker
            singlePickerLauncher = componentActivity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        handleSingleResult(uri);
                    }
                }
            );
            
            // Multiple image picker - pre-register with max count
            multiplePickerLauncher = componentActivity.registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_SELECTION_COUNT),
                new ActivityResultCallback<List<Uri>>() {
                    @Override
                    public void onActivityResult(List<Uri> uris) {
                        handleMultipleResults(uris);
                    }
                }
            );
            
            launchersInitialized = true;
            Log.d(TAG, "Photo Picker launchers initialized successfully");
            return true;
            
        } catch (IllegalStateException e) {
            // Activity is already started, cannot register launchers
            Log.w(TAG, "Cannot register launchers - activity already started. Will use fallback.", e);
            singlePickerLauncher = null;
            multiplePickerLauncher = null;
            launchersInitialized = false;
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing launchers: " + e.getMessage(), e);
            singlePickerLauncher = null;
            multiplePickerLauncher = null;
            launchersInitialized = false;
            return false;
        }
    }
    
    /**
     * Check if launchers are available and try to initialize if not
     */
    private boolean ensureLaunchersReady() {
        if (launchersInitialized) {
            return true;
        }
        
        // Try to initialize launchers
        return initializeLaunchers();
    }
    
    /**
     * Launch the Photo Picker
     * Returns true if launcher was successfully started, false if it couldn't be initialized
     */
    public boolean launch(JSONObject params, CallbackContext callback) {
        this.options = params;
        this.callbackContext = callback;
        
        // First check if we can initialize the launchers
        if (!ensureLaunchersReady()) {
            Log.w(TAG, "Photo Picker launchers could not be initialized");
            return false;
        }
        
        try {
            int maxImages = params.optInt("maximumImagesCount", 15);
            
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    launchPicker(maxImages);
                }
            });
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching Photo Picker", e);
            callback.error("Failed to launch Photo Picker: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Launch the appropriate picker based on max images
     */
    private void launchPicker(int maxImages) {
        // Launchers should already be ready (checked in launch method)
        if (singlePickerLauncher == null || multiplePickerLauncher == null) {
            Log.e(TAG, "Launchers are null when they shouldn't be");
            callbackContext.error("Photo Picker initialization error");
            return;
        }
        
        try {
            // Build the media type request
            PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
            
            if (maxImages == 1) {
                // Use single picker
                singlePickerLauncher.launch(request);
            } else {
                // Use multiple picker (will be limited by the pre-registered max count)
                // Note: The actual selection will be limited to min(maxImages, MAX_SELECTION_COUNT)
                multiplePickerLauncher.launch(request);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in launchPicker", e);
            callbackContext.error("Failed to launch picker: " + e.getMessage());
        }
    }
    
    /**
     * Handle single image selection result
     */
    private void handleSingleResult(Uri uri) {
        if (uri == null) {
            // User cancelled
            callbackContext.success(new JSONArray());
            return;
        }
        
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        processResults(uris);
    }
    
    /**
     * Handle multiple image selection results
     */
    private void handleMultipleResults(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            // User cancelled or selected nothing
            callbackContext.success(new JSONArray());
            return;
        }
        
        processResults(uris);
    }
    
    /**
     * Process the selected URIs and return results
     */
    private void processResults(List<Uri> uris) {
        try {
            // Get processing options
            int desiredWidth = options.optInt("width", 0);
            int desiredHeight = options.optInt("height", 0);
            int quality = options.optInt("quality", 100);
            int outputType = options.optInt("outputType", 0); // 0 = FILE_URI, 1 = BASE64
            
            // Get thumbnail options
            boolean includeThumbnail = options.optBoolean("includeThumbnail", true);
            int thumbnailWidth = options.optInt("thumbnailWidth", 200);
            int thumbnailHeight = options.optInt("thumbnailHeight", 200);
            
            // Process images using PhotoPickerUtils with enhanced metadata
            PhotoPickerUtils utils = new PhotoPickerUtils(cordova.getActivity());
            JSONArray results;
            
            if (includeThumbnail) {
                // Use enhanced processing with thumbnails
                results = utils.processImagesWithThumbnails(uris, desiredWidth, desiredHeight, 
                                                           quality, outputType, includeThumbnail,
                                                           thumbnailWidth, thumbnailHeight);
            } else {
                // Use standard processing for backward compatibility
                results = utils.processImages(uris, desiredWidth, desiredHeight, quality, outputType);
            }
            
            // Return results
            callbackContext.success(results);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing results", e);
            callbackContext.error("Failed to process images: " + e.getMessage());
        }
    }
}