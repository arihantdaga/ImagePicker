# Testing the Updated ImagePicker Plugin

## Prerequisites
1. Make sure you have Android Studio installed
2. Ensure your test device or emulator has Google Play Services (for Photo Picker backport on older devices)

## Installation Steps

### 1. Remove existing plugin (if installed)
```bash
cd /Users/arihantdaga/Documents/dev/theopendiaries/Todmobileapp
ionic cordova plugin remove cordova-plugin-telerik-imagepicker
```

### 2. Install the updated plugin from local directory
```bash
# Using plugman (recommended for testing)
plugman install --platform android --project platforms/android --plugin ./local_plugins/ImagePicker

# OR using Cordova CLI
ionic cordova plugin add ./local_plugins/ImagePicker
```

### 3. Build the Android app
```bash
ionic cordova build android
```

## Testing in Android Studio

### 1. Open the project
- Open Android Studio
- File → Open → Navigate to `platforms/android`
- Let Gradle sync complete

### 2. Check for build errors
- Look for any compilation errors in the Build window
- Common issues to check:
  - AndroidX migration conflicts
  - Missing dependencies
  - Import errors

### 3. Run on different Android versions

Test on the following configurations:

| Test Device | Android Version | Expected Behavior |
|------------|-----------------|-------------------|
| Emulator/Device 1 | Android 14 (API 34) | Native Photo Picker, no permissions |
| Emulator/Device 2 | Android 13 (API 33) | Native Photo Picker, no permissions |
| Emulator/Device 3 | Android 11 (API 30) | Backported Photo Picker via Google Play |
| Emulator/Device 4 | Android 9 (API 28) | Backported Photo Picker via Google Play |
| Emulator/Device 5 | Android 7 (API 24) | Backported Photo Picker or fallback to MultiImageChooserActivity |

### 4. Test scenarios

For each device, test:

1. **Single Image Selection**
   ```javascript
   window.imagePicker.getPictures(
     function(results) {
       console.log('Image selected:', results);
     },
     function(error) {
       console.error('Error:', error);
     },
     {
       maximumImagesCount: 1
     }
   );
   ```

2. **Multiple Image Selection**
   ```javascript
   window.imagePicker.getPictures(
     function(results) {
       console.log('Images selected:', results);
     },
     function(error) {
       console.error('Error:', error);
     },
     {
       maximumImagesCount: 5
     }
   );
   ```

3. **With Image Resizing**
   ```javascript
   window.imagePicker.getPictures(
     function(results) {
       console.log('Resized images:', results);
     },
     function(error) {
       console.error('Error:', error);
     },
     {
       maximumImagesCount: 3,
       width: 800,
       height: 800,
       quality: 80
     }
   );
   ```

4. **Base64 Output**
   ```javascript
   window.imagePicker.getPictures(
     function(results) {
       console.log('Base64 images:', results);
     },
     function(error) {
       console.error('Error:', error);
     },
     {
       maximumImagesCount: 2,
       outputType: window.imagePicker.OutputType.BASE64_STRING
     }
   );
   ```

## Debugging Tips

### Check Logcat for errors
```bash
adb logcat | grep -E "ImagePicker|PhotoPicker"
```

### Common issues and solutions:

1. **Photo Picker not showing on older devices**
   - Check if Google Play Services is installed and updated
   - Look for log message: "Photo Picker availability: true/false"

2. **ActivityNotFoundException**
   - Ensure the manifest includes the Photo Picker backport service
   - Check that AndroidX Activity 1.7.0+ is properly included

3. **Permission issues on fallback**
   - The legacy picker still needs READ_EXTERNAL_STORAGE permission
   - Check permission status in device settings

4. **Build errors**
   - Clean and rebuild: `ionic cordova clean android && ionic cordova build android`
   - Check for AndroidX migration issues
   - Verify all dependencies in build.gradle

## Expected Log Output

When Photo Picker is used successfully:
```
D/PhotoPickerLauncher: Photo Picker is available
D/PhotoPickerLauncher: Launching Photo Picker with max images: 5
D/PhotoPickerUtils: Processing 3 selected images
D/ImagePicker: Photo Picker results returned successfully
```

When falling back to legacy picker:
```
D/PhotoPickerLauncher: Photo Picker not available, using fallback
D/ImagePicker: Using MultiImageChooserActivity
D/ImagePicker: Requesting READ_EXTERNAL_STORAGE permission
```

## Verification Checklist

- [ ] Plugin installs without errors
- [ ] App builds successfully
- [ ] Photo Picker launches on Android 13+
- [ ] Photo Picker launches on Android 11-12 (with backport)
- [ ] Fallback works on older devices without Google Play Services
- [ ] Single image selection works
- [ ] Multiple image selection works
- [ ] Image resizing works correctly
- [ ] Base64 output format works
- [ ] File URI output format works
- [ ] No permission prompts on Android 13+ with Photo Picker
- [ ] Permission handling works correctly for fallback

## Report Issues

When reporting issues, please include:
1. Android version and API level
2. Device model or emulator configuration
3. Logcat output filtered for ImagePicker/PhotoPicker
4. Steps to reproduce
5. Expected vs actual behavior