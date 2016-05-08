package com.iceteck.silicompressorr;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Toure
 * @date 28/03/2016
 * Created by Toure on 28/03/2016.
 */
public class SiliCompressor {

    private static final String LOG_TAG = SiliCompressor.class.getSimpleName();

    static volatile SiliCompressor singleton = null;
    private static Context mContext;

    public SiliCompressor(Context context){
        mContext = context;
    }

    // initialise the class and set the context
    public static SiliCompressor with(Context context){
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
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return filepath
     */
    public String compress(String imageUri){
        return compressImage(imageUri);
    }

    /**
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return filepath
     */
    public String compress(String imageUri, boolean deleteSourceImage){

        String compressUri =  compressImage(imageUri);

        if (deleteSourceImage)
        {
            File source = new File(getRealPathFromURI(imageUri));
            if (source.exists()) {
                boolean isdeleted = source.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }
        }

        return compressUri;
    }


    /**
     * Compresses the image at the specified Uri String and and return the bitmap data of the compressed image.
     *
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return Bitmap format of the new image file (compressed)
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imageUri) throws IOException {
        File imageFile = new File(compressImage(imageUri));
        Uri newImageUri = Uri.fromFile(imageFile);
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), newImageUri);
        return bitmap;
    }

    /**
     * Compresses the image at the specified Uri String and and return the bitmap data of the compressed image.
     * @param imageUri Uri (String) of the source image you wish to compress
     * @param deleteSourceImage If True will delete the source file
     * @return
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imageUri, boolean deleteSourceImage) throws IOException {
        File imageFile = new File(compressImage(imageUri));
        Uri newImageUri = Uri.fromFile(imageFile);
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), newImageUri);

        if (deleteSourceImage)
        {
           File source = new File(getRealPathFromURI(imageUri));
            if (source.exists()) {
                boolean isdeleted = source.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }
        }
        return bitmap;
    }

    // Actually does the compression of the Image
    private String compressImage(String imageUri) {

        String filePath = getRealPathFromURI(imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;

//      max Height and width values of the compressed image is taken as 816x612

        float maxHeight = 816.0f;
        float maxWidth = 612.0f;
        float imgRatio = actualWidth / actualHeight;
        float maxRatio = maxWidth / maxHeight;

//      width and height values are set maintaining the aspect ratio of the image

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

//      setting inSampleSize value allows to load a scaled down version of the original image

        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
        options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[16 * 1024];

        try {
//          load the bitmap from its path
            bmp = BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();

        }
        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();
        }

        float ratioX = actualWidth / (float) options.outWidth;
        float ratioY = actualHeight / (float) options.outHeight;
        float middleX = actualWidth / 2.0f;
        float middleY = actualHeight / 2.0f;

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

//      check the rotation of the image and display it properly
        ExifInterface exif;
        try {
            exif = new ExifInterface(filePath);

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

        FileOutputStream out = null;
        String filename = getFilename();
        try {
            out = new FileOutputStream(filename);

//          write the compressed bitmap at the destination specified by filename.
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return filename;

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

    private String getFilename() {
        File file = new File(Environment.getExternalStorageDirectory().getPath(), "SiliCompressor/Images");
        if (!file.exists()) {
            file.mkdirs();
        }
        String uriSting = (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ".jpg");
        return uriSting;

    }

    private String getRealPathFromURI(String contentURI) {
        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = mContext.getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        } else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(index);
        }
    }

    /**
     * Fluent API for creating {@link SiliCompressor} instances.
     */
    public static class Builder {

        private final Context context;

        /**
         * Start building a new {@link SiliCompressor} instance.
         */
        public Builder(Context context) {
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

    class ImageCompressionAsyncTask extends AsyncTask<String, Void, String> {
        ProgressDialog mProgressDialog;

        public ImageCompressionAsyncTask(Context context) {

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected String doInBackground(String... params) {

            String filePath = compressImage(params[0]);
            return filePath;
        }

        @Override
        protected void onPostExecute(String s) {


        }
    }

}
