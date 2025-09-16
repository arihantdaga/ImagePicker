/**
 * An Image Picker Plugin for Cordova/PhoneGap.
 */
package com.synconset;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.ComponentActivity;

public class ImagePicker extends CordovaPlugin {

    private static final String ACTION_GET_PICTURES = "getPictures";
    private static final String ACTION_HAS_READ_PERMISSION = "hasReadPermission";
    private static final String ACTION_REQUEST_READ_PERMISSION = "requestReadPermission";

    private static final int PERMISSION_REQUEST_CODE = 100;

    private CallbackContext callbackContext;
    private Intent imagePickerIntent;
    private PhotoPickerLauncher photoPickerLauncher;
    private JSONObject pendingOptions;


    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        // Initialize Photo Picker launcher if available
        try {
            if (cordova.getActivity() instanceof ComponentActivity) {
                photoPickerLauncher = new PhotoPickerLauncher(cordova, this);
            }
        } catch (Exception e) {
            // Failed to initialize Photo Picker, will use legacy picker
            android.util.Log.e("ImagePicker", "Failed to initialize PhotoPickerLauncher: " + e.getMessage());
            photoPickerLauncher = null;
        }
    }

    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (ACTION_HAS_READ_PERMISSION.equals(action)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasReadPermission()));
            return true;

        } else if (ACTION_REQUEST_READ_PERMISSION.equals(action)) {
            requestReadPermission();
            return true;

        } else if (ACTION_GET_PICTURES.equals(action)) {
            final JSONObject params = args.getJSONObject(0);
            this.pendingOptions = params;
            
            // Check if Photo Picker is available and use it
            android.util.Log.d("ImagePicker", "Photo Picker available: " + PhotoPickerLauncher.isPhotoPickerAvailable() + ", launcher: " + (photoPickerLauncher != null));
            if (PhotoPickerLauncher.isPhotoPickerAvailable() && photoPickerLauncher != null) {
                // Try to use Photo Picker
                android.util.Log.d("ImagePicker", "Attempting to launch Photo Picker");
                boolean launched = photoPickerLauncher.launch(params, callbackContext);
                if (launched) {
                    android.util.Log.d("ImagePicker", "Photo Picker launched successfully");
                    return true;
                }
                // If not launched, fall through to legacy picker
                android.util.Log.w("ImagePicker", "Photo Picker could not be launched, falling back to legacy picker");
            } else {
                android.util.Log.d("ImagePicker", "Using legacy picker directly");
            }
            
            // Fall back to legacy implementation
            launchLegacyPicker(params);
            return true;
        }
        return false;
    }

    @SuppressLint("InlinedApi")
    private boolean hasReadPermission() {
         String readImagePermission = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readImagePermission = Manifest.permission.READ_MEDIA_IMAGES;
        }
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this.cordova.getActivity(), readImagePermission);

        // return cordova!=null && cordova.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);

//        return Build.VERSION.SDK_INT < 23 ||
//            PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    private void requestReadPermission() {
//        if (!hasReadPermission()) {
//            ActivityCompat.requestPermissions(
//                this.cordova.getActivity(),
//                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
//                PERMISSION_REQUEST_CODE);
//        }
//        cordova.requestPermission(
//                this,
//                PERMISSION_REQUEST_CODE,
//                Manifest.permission.READ_EXTERNAL_STORAGE
//        );
        String readImagePermission = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readImagePermission = Manifest.permission.READ_MEDIA_IMAGES;
        }

        if (!hasReadPermission()) {
            ActivityCompat.requestPermissions(
                this.cordova.getActivity(),
                new String[] { readImagePermission },
                PERMISSION_REQUEST_CODE);
        }

        // if(ActivityCompat.shouldShowRequestPermissionRationale(this.cordova.getActivity(),
        // Manifest.permission.READ_EXTERNAL_STORAGE)){
        //     ActivityCompat.requestPermissions(
        //         this.cordova.getActivity(),
        //         new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
        //         PERMISSION_REQUEST_CODE);
        // }else{
        //     ActivityCompat.requestPermissions(
        //         this.cordova.getActivity(),
        //         new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
        //         PERMISSION_REQUEST_CODE);
        // }
        // This method executes async and we seem to have no known way to receive the result
        // (that's why these methods were later added to Cordova), so simply returning ok now.
        callbackContext.success();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            int sync = data.getIntExtra("bigdata:synccode", -1);
            final Bundle bigData = ResultIPC.get().getLargeData(sync);

            // Check for enhanced results first
            String enhancedResults = bigData.getString("ENHANCED_RESULTS");
            if (enhancedResults != null) {
                try {
                    // Parse the JSON array string
                    JSONArray res = new JSONArray(enhancedResults);
                    callbackContext.success(res);
                } catch (JSONException e) {
                    // Fall back to legacy format
                    ArrayList<String> fileNames = bigData.getStringArrayList("MULTIPLEFILENAMES");
                    JSONArray res = new JSONArray(fileNames);
                    callbackContext.success(res);
                }
            } else {
                // Legacy format
                ArrayList<String> fileNames = bigData.getStringArrayList("MULTIPLEFILENAMES");
                JSONArray res = new JSONArray(fileNames);
                callbackContext.success(res);
            }

        } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
            String error = data.getStringExtra("ERRORMESSAGE");
            callbackContext.error(error);

        } else if (resultCode == Activity.RESULT_CANCELED) {
            JSONArray res = new JSONArray();
            callbackContext.success(res);

        } else {
            callbackContext.error("No images selected");
        }
    }

/**
     * Choosing a picture launches another Activity, so we need to implement the
     * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
     * before we get the launched Activity's result.
     *
     * @see http://cordova.apache.org/docs/en/dev/guide/platforms/android/plugin.html#launching-other-activities
     */
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }


    @Override
    public void onRequestPermissionResult(int requestCode,
                                          String[] permissions,
                                          int[] grantResults) throws JSONException {
        // For now we just have one permission, so things can be kept simple...
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cordova.startActivityForResult(this, this.imagePickerIntent, 0);
        } else {
            // Tell the JS layer that something went wrong...
            callbackContext.error("Permission denied");
        }
    }
    
    /**
     * Launch the legacy image picker
     */
    @SuppressLint("InlinedApi")
    private void launchLegacyPicker(JSONObject params) throws JSONException {
        this.imagePickerIntent = new Intent(cordova.getActivity(), MultiImageChooserActivity.class);
        int max = 20;
        int desiredWidth = 0;
        int desiredHeight = 0;
        int quality = 100;
        int outputType = 0;
        boolean includeThumbnail = true;
        int thumbnailWidth = 200;
        int thumbnailHeight = 200;
        
        if (params.has("maximumImagesCount")) {
            max = params.getInt("maximumImagesCount");
        }
        if (params.has("width")) {
            desiredWidth = params.getInt("width");
        }
        if (params.has("height")) {
            desiredHeight = params.getInt("height");
        }
        if (params.has("quality")) {
            quality = params.getInt("quality");
        }
        if (params.has("outputType")) {
            outputType = params.getInt("outputType");
        }
        if (params.has("includeThumbnail")) {
            includeThumbnail = params.getBoolean("includeThumbnail");
        }
        if (params.has("thumbnailWidth")) {
            thumbnailWidth = params.getInt("thumbnailWidth");
        }
        if (params.has("thumbnailHeight")) {
            thumbnailHeight = params.getInt("thumbnailHeight");
        }

        imagePickerIntent.putExtra("MAX_IMAGES", max);
        imagePickerIntent.putExtra("WIDTH", desiredWidth);
        imagePickerIntent.putExtra("HEIGHT", desiredHeight);
        imagePickerIntent.putExtra("QUALITY", quality);
        imagePickerIntent.putExtra("OUTPUT_TYPE", outputType);
        imagePickerIntent.putExtra("INCLUDE_THUMBNAIL", includeThumbnail);
        imagePickerIntent.putExtra("THUMBNAIL_WIDTH", thumbnailWidth);
        imagePickerIntent.putExtra("THUMBNAIL_HEIGHT", thumbnailHeight);

        // Determine which permission to check based on Android version
        String readImagePermission = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readImagePermission = Manifest.permission.READ_MEDIA_IMAGES;
        }
        
        // Check permissions for legacy picker
        if (cordova != null) {
            if (hasReadPermission()) {
                cordova.startActivityForResult(this, imagePickerIntent, 0);
            } else {
                cordova.requestPermission(
                        this,
                        PERMISSION_REQUEST_CODE,
                        readImagePermission
                );
            }
        }
    }

}
