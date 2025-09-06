package com.synconset;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class PhotoPickerUtils {
    private static final String TAG = "PhotoPickerUtils";
    private static final int OUTPUT_TYPE_FILE_URI = 0;
    private static final int OUTPUT_TYPE_BASE64 = 1;
    
    private Context context;
    private ContentResolver contentResolver;
    
    public PhotoPickerUtils(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }
    
    /**
     * Process a list of image URIs according to the specified options
     */
    public JSONArray processImages(List<Uri> uris, int desiredWidth, int desiredHeight, 
                                  int quality, int outputType) throws JSONException {
        JSONArray results = new JSONArray();
        
        for (Uri uri : uris) {
            String result = processImage(uri, desiredWidth, desiredHeight, quality, outputType);
            if (result != null) {
                results.put(result);
            }
        }
        
        return results;
    }
    
    /**
     * Process a single image URI
     */
    private String processImage(Uri uri, int desiredWidth, int desiredHeight, 
                               int quality, int outputType) {
        try {
            // Load and process the image
            Bitmap bitmap = loadBitmap(uri, desiredWidth, desiredHeight);
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap from URI: " + uri);
                return null;
            }
            
            // Correct orientation if needed
            bitmap = correctOrientation(bitmap, uri);
            
            // Scale if needed
            if (desiredWidth > 0 || desiredHeight > 0) {
                bitmap = scaleBitmap(bitmap, desiredWidth, desiredHeight);
            }
            
            // Return based on output type
            if (outputType == OUTPUT_TYPE_BASE64) {
                return bitmapToBase64(bitmap, quality);
            } else {
                return saveBitmapToFile(bitmap, quality);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + uri, e);
            return null;
        }
    }
    
    /**
     * Load bitmap from URI with optional size constraints
     */
    private Bitmap loadBitmap(Uri uri, int maxWidth, int maxHeight) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(uri);
        if (inputStream == null) {
            return null;
        }
        
        // First decode with inJustDecodeBounds=true to check dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();
        
        // Calculate inSampleSize
        if (maxWidth > 0 || maxHeight > 0) {
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
        }
        
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        inputStream = contentResolver.openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();
        
        return bitmap;
    }
    
    /**
     * Calculate sample size for efficient bitmap loading
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if ((reqHeight > 0 && height > reqHeight) || (reqWidth > 0 && width > reqWidth)) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    /**
     * Correct image orientation based on EXIF data
     */
    private Bitmap correctOrientation(Bitmap bitmap, Uri uri) {
        try {
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null) {
                return bitmap;
            }
            
            ExifInterface exif = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                exif = new ExifInterface(inputStream);
            } else {
                // For older versions, try to get file path
                String path = getPathFromUri(uri);
                if (path != null) {
                    exif = new ExifInterface(path);
                }
            }
            inputStream.close();
            
            if (exif == null) {
                return bitmap;
            }
            
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 
                                                  ExifInterface.ORIENTATION_NORMAL);
            
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1, -1);
                    break;
                default:
                    return bitmap;
            }
            
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, 
                                                       bitmap.getWidth(), bitmap.getHeight(), 
                                                       matrix, true);
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }
            return rotatedBitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error correcting orientation", e);
            return bitmap;
        }
    }
    
    /**
     * Scale bitmap to fit within specified dimensions
     */
    private Bitmap scaleBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Calculate scale to fit within bounds
        float scale = 1.0f;
        if (maxWidth > 0 && width > maxWidth) {
            scale = (float) maxWidth / width;
        }
        if (maxHeight > 0 && height > maxHeight) {
            float scaleHeight = (float) maxHeight / height;
            scale = Math.min(scale, scaleHeight);
        }
        
        if (scale >= 1.0f) {
            return bitmap;
        }
        
        // Scale the bitmap
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        if (scaledBitmap != bitmap) {
            bitmap.recycle();
        }
        
        return scaledBitmap;
    }
    
    /**
     * Convert bitmap to Base64 string
     */
    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
    
    /**
     * Save bitmap to file and return file URI
     */
    private String saveBitmapToFile(Bitmap bitmap, int quality) throws IOException {
        // Create a unique file name
        String fileName = "image_" + UUID.randomUUID().toString() + ".jpg";
        File outputDir = new File(context.getCacheDir(), "photo_picker_images");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        File outputFile = new File(outputDir, fileName);
        
        OutputStream outputStream = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        outputStream.flush();
        outputStream.close();
        
        return Uri.fromFile(outputFile).toString();
    }
    
    /**
     * Get file path from URI (for older Android versions)
     */
    private String getPathFromUri(Uri uri) {
        String path = null;
        
        if ("content".equals(uri.getScheme())) {
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = contentResolver.query(uri, projection, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    path = cursor.getString(columnIndex);
                }
                cursor.close();
            }
        } else if ("file".equals(uri.getScheme())) {
            path = uri.getPath();
        }
        
        return path;
    }
    
    /**
     * Get display name from URI
     */
    public String getDisplayName(Uri uri) {
        String displayName = null;
        
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
                cursor.close();
            }
        }
        
        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }
        
        return displayName;
    }
}