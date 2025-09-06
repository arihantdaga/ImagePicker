declare module 'cordova-plugin-imagepicker' {
    interface ImagePickerOptions {
        /**
         * Maximum number of images to select. Default: 15
         */
        maximumImagesCount?: number;
        
        /**
         * Width to resize image to. If 0, will use original width. Default: 0
         */
        width?: number;
        
        /**
         * Height to resize image to. If 0, will use original height. Default: 0
         */
        height?: number;
        
        /**
         * Quality of resized image (0-100). Default: 100
         */
        quality?: number;
        
        /**
         * Output type - FILE_URI (0) or BASE64_STRING (1). Default: FILE_URI
         */
        outputType?: number;
        
        /**
         * Include thumbnail with enhanced metadata. Default: true
         */
        includeThumbnail?: boolean;
        
        /**
         * Width of thumbnail. Default: 200
         */
        thumbnailWidth?: number;
        
        /**
         * Height of thumbnail. Default: 200
         */
        thumbnailHeight?: number;
        
        /**
         * Allow video selection. Default: false
         */
        allow_video?: boolean;
        
        /**
         * Title for the picker. Default: 'Select an Album'
         */
        title?: string;
        
        /**
         * Message for the picker. Default: null
         */
        message?: string;
        
        /**
         * Disable popover on iPad. Default: false
         */
        disable_popover?: boolean;
    }
    
    interface ImagePickerResult {
        /**
         * Original image path (file URI or base64 data URI)
         */
        originalPath: string;
        
        /**
         * Original file name
         */
        fileName: string;
        
        /**
         * File size in bytes
         */
        fileSize: number;
        
        /**
         * MIME type of the image
         */
        mimeType: string;
        
        /**
         * Original image width in pixels
         */
        width: number;
        
        /**
         * Original image height in pixels
         */
        height: number;
        
        /**
         * Base64 encoded thumbnail (data URI format)
         */
        thumbnail?: string;
        
        /**
         * Thumbnail width in pixels
         */
        thumbnailWidth?: number;
        
        /**
         * Thumbnail height in pixels
         */
        thumbnailHeight?: number;
        
        /**
         * Android content URI (Android only)
         */
        contentUri?: string;
    }
    
    interface ImagePicker {
        OutputType: {
            FILE_URI: 0;
            BASE64_STRING: 1;
        };
        
        /**
         * Get pictures from the device
         * @param success Success callback with array of results
         * @param fail Error callback
         * @param options Options for image selection
         */
        getPictures(
            success: (results: ImagePickerResult[] | string[]) => void,
            fail: (error: string) => void,
            options?: ImagePickerOptions
        ): void;
        
        /**
         * Check if app has read permission
         * @param callback Callback with boolean result
         */
        hasReadPermission(callback: (hasPermission: boolean) => void): void;
        
        /**
         * Request read permission
         * @param callback Success callback
         * @param failureCallback Error callback
         */
        requestReadPermission(
            callback: () => void,
            failureCallback?: (error: string) => void
        ): void;
    }
    
    interface Window {
        imagePicker: ImagePicker;
    }
}

export {};