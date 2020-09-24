package com.iceteck.silicompressorr;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.iceteck.silicompressorr.videocompression.MediaController;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author Toure, Akah L
 * Created by Toure on 28/03/2016.
 */
public class SiliCompressor {

    private static final String LOG_TAG = SiliCompressor.class.getSimpleName();
    public static String videoCompressionPath;
    private static final String IMAGE_DESTINATION = "Silicompressor/images";

    static volatile SiliCompressor singleton = null;
    private static Context mContext;
    private static final String FILE_PROVIDER_AUTHORITY = ".iceteck.silicompressor.provider";

    public SiliCompressor(Context context) {
        mContext = context;
    }

    // initialise the class and set the context
    public static SiliCompressor with(Context context) {
        if (singleton == null) {
            synchronized (SiliCompressor.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;

    }

    /**
     * Deletes image file for a given path.
     *
     * @param imageUri The path of the photo to be deleted.
     * @return True if the file is deleted and False otherwise
     */
    private static boolean deleteImageFile(String imageUri) {
        Uri uri = Uri.parse(imageUri);
        int cnt = mContext.getContentResolver().delete(uri, null, null);
        return cnt > 0;
    }

    /**
     * Compress the image at with the specified path and return the filepath of the compressed image.
     *
     * @param uriString   The uriString of the image file you wish to compress.
     * @param destination The destination directory where the compressed image will be stored.
     * @return filepath  The path of the compressed image file
     */
    public String compress(String uriString, File destination) {
        return compressImage(uriString, destination);
    }

    static String getAuthorities(@NonNull Context context) {
        return context.getPackageName() + FILE_PROVIDER_AUTHORITY;
    }


    /**
     * Compress the image at with the specified path and return the bitmap data of the compressed image.
     *
     * @param imagePath The path of the image file you wish to compress
     * @return Bitmap format of the new image file (compressed)
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imagePath) throws IOException {

        // Return the required bitmap
        return getCompressBitmap(imagePath, false);

    }

    /**
     * Compress the image at with the specified path and return the filepath of the compressed image.
     *
     * @param uriString         The uriString of the image file you wish to compress.
     * @param destination       The destination directory where the compressed image will be stored.
     * @param deleteSourceImage if True will delete the original file
     * @return filepath  The path of the compressed image file
     */
    public String compress(String uriString, File destination, boolean deleteSourceImage) {

        String compressedImagePath = compressImage(uriString, destination);

        if (deleteSourceImage) {
            boolean isdeleted = deleteImageFile(uriString);
            Log.d(LOG_TAG, (isdeleted) ? "Source image file deleted" : "Error: Source image file not deleted.");

        }

        return compressedImagePath;
    }

    /**
     * Compress the image at with the specified path and return the bitmap data of the compressed image.
     *
     * @param imageUri          The String uri of the image file you wish to compress.
     * @param deleteSourceImage If True will delete the source file
     * @return Compress image bitmap
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imageUri, boolean deleteSourceImage) throws IOException {

        String compressedImageUriString = compressImage(imageUri, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), IMAGE_DESTINATION));
        Bitmap bitmap = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(compressedImageUriString));
        } else {
            bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.fromFile(new File(compressedImageUriString)));
        }

        if (deleteSourceImage) {
            boolean isdeleted = deleteImageFile(imageUri);
            Log.d(LOG_TAG, (isdeleted) ? "Source image file deleted" : "Error: Source image file not deleted.");
        }

        // Return the required bitmap
        return bitmap;
    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     * This uses default values for the converted videos
     *
     * @param videoFilePath  source path for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @return The Path of the compressed video file
     */
    public String compressVideo(String videoFilePath, String destinationDir) throws URISyntaxException {
        return compressVideo(videoFilePath, destinationDir, 0, 0, 0);
    }

    /**
     * This method compresses the video using the content uri of the video
     * @param videoContentUri
     * @param destinationDir destination directory where converted file should be saved
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoContentUri, String destinationDir) throws URISyntaxException {
        return compressVideo(videoContentUri, destinationDir, 0, 0, 0);
    }


    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     *
     * @param videoFilePath  source path for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(String videoFilePath, String destinationDir, int outWidth, int outHeight, int bitrate) throws URISyntaxException {
        boolean isconverted = MediaController.getInstance().convertVideo(videoFilePath, new File(destinationDir), outWidth, outHeight, bitrate);
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete");
        } else {
            Log.v(LOG_TAG, "Video conversion in progress");
        }

        return MediaController.cachedFile.getPath();

    }

    /**
     *  This method compresses the video using the content uri of the video
     * @param videoContentUri
     * @param destinationDir destination directory where converted file should be saved
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoContentUri, String destinationDir, int outWidth, int outHeight, int bitrate) throws URISyntaxException {
        boolean isConverted = MediaController.getInstance().convertVideo(mContext, videoContentUri, new File(destinationDir), outWidth, outHeight, bitrate);
        if (isConverted) {
            Log.v(LOG_TAG, "Video Conversion Complete");
        } else {
            Log.v(LOG_TAG, "Video conversion in progress");
        }

        return MediaController.cachedFile.getPath();
    }


    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    /**
     * Get the file path of the compressed file
     *
     * @param filename
     * @param file     Destination directory
     * @return
     */
    private String getFilename(String filename, File file) {
        if (!file.exists()) {
            file.mkdirs();
        }
        String ext = ".jpg";
        //get extension
        /*if (Pattern.matches("^[.][p][n][g]", filename)){
            ext = ".png";
        }*/
        String nameFirstPart = file.getAbsolutePath() + "/IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) ;
        String nameFull = nameFirstPart + ext;
        int x = 1;
        while (new File(nameFull).exists()){
            nameFull = nameFirstPart + "_" + x + ext;
            x++;
        }

        return nameFull;
    }

    /**
     * Does the actual image compression
     *
     * @param uriString     The uriString of the image file you wish to compress.
     * @param destDirectory destination directory where the compressed image will be stored.
     * @return The path of the compressed image file
     */
    private String compressImage(String uriString, File destDirectory) {
        try {
            Uri imageUri = Uri.parse(uriString);
            Bitmap scaledBitmap = null;

            BitmapFactory.Options options = new BitmapFactory.Options();

            // by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
            // you try the use the bitmap here, you will get null.
            options.inJustDecodeBounds = true;
            Bitmap bmp = BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(imageUri), null, options);

            int actualHeight = options.outHeight;
            int actualWidth = options.outWidth;

            // max Height and width values of the compressed image is taken as 816x612
            float maxHeight = 816.0f;
            float maxWidth = 612.0f;
            float imgRatio = actualWidth / actualHeight;
            float maxRatio = maxWidth / maxHeight;

            // width and height values are set maintaining the aspect ratio of the image

            if (actualHeight > maxHeight || actualWidth > maxWidth) {
                if (imgRatio < maxRatio) {
                    imgRatio = maxHeight / actualHeight;
                    actualWidth = (int) (imgRatio * actualWidth);
                    actualHeight = (int) maxHeight;
                } else if (imgRatio > maxRatio) {
                    imgRatio = maxWidth / actualWidth;
                    actualHeight = (int) (imgRatio * actualHeight);
                    actualWidth = (int) maxWidth;
                } else {
                    actualHeight = (int) maxHeight;
                    actualWidth = (int) maxWidth;

                }
            }

            // setting inSampleSize value allows to load a scaled down version of the original image
            options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

            // inJustDecodeBounds set to false to load the actual bitmap
            options.inJustDecodeBounds = false;

            // this options allow android to claim the bitmap memory if it runs low on memory
            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inTempStorage = new byte[16 * 1024];

            // load the bitmap from its path
            bmp = BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(imageUri), null, options);
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);


            float ratioX = actualWidth / (float) options.outWidth;
            float ratioY = actualHeight / (float) options.outHeight;
            float middleX = actualWidth / 2.0f;
            float middleY = actualHeight / 2.0f;

            Matrix scaleMatrix = new Matrix();
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

            Canvas canvas = new Canvas(scaledBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

            // check the rotation of the image and display it properly
            androidx.exifinterface.media.ExifInterface exif;
            try {
                exif = new ExifInterface(mContext.getContentResolver().openInputStream(imageUri));

                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, 0);
                Log.d("EXIF", "Exif: " + orientation);
                Matrix matrix = new Matrix();
                if (orientation == 6) {
                    matrix.postRotate(90);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 3) {
                    matrix.postRotate(180);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 8) {
                    matrix.postRotate(270);
                    Log.d("EXIF", "Exif: " + orientation);
                }
                scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                        scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                        true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SiliCompressor/");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri resultUri = mContext.getContentResolver().insert(collection, values);

                OutputStream out = mContext.getContentResolver().openOutputStream(resultUri);

                // write the compressed bitmap at the destination specified by filename.
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                mContext.getContentResolver().update(resultUri, values, null, null);

                return resultUri.toString();

            } else {
                String filename = getFilename(uriString, destDirectory);

                FileOutputStream out = new FileOutputStream(filename);

                //write the compressed bitmap at the destination specified by filename.
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

                return filename;
            }

        } catch (FileNotFoundException | OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * Compress drawable file with specified drawableID
     *
     * @param drawableID ID of the drawable file to compress
     * @return The path to the compressed file or null if the could not access drawable file
     * @throws IOException
     */
    public String compress(int drawableID) {
        try {
            // Create a bitmap from this drawable
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getApplicationContext().getResources(), drawableID);
            if (null != bitmap) {
                // Create a file from the bitmap

                // Create an image file name
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date());
                String imageFileName = "JPEG_" + timeStamp + "_";
                File storageDir = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );

                FileOutputStream out = new FileOutputStream(image);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.close();

                // Compress the new file
                Uri copyImageUri = FileProvider.getUriForFile(mContext, getAuthorities(mContext), image);

                String compressedImagePath = compressImage(copyImageUri.toString(), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), IMAGE_DESTINATION));

                // Delete the file created from the drawable Id
                if (image.exists()) {
                    deleteImageFile(copyImageUri.toString());
                }

                // return the path to the compressed image
                return compressedImagePath;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Fluent API for creating {@link SiliCompressor} instances.
     */
    public static class Builder {

        private final Context context;


        /**
         * @param context Context from which the library is called
         *                Start building a new {@link SiliCompressor} instance.
         */
        Builder(@NonNull Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }


        /**
         * Create the {@link SiliCompressor} instance.
         */
        public SiliCompressor build() {
            Context context = this.context;

            return new SiliCompressor(context);
        }
    }
}
