package com.iceteck.silicompressor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.iceteck.silicompressorr.SiliCompressor;
import com.iceteck.silicompressorr.listener.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class SelectPictureActivity extends AppCompatActivity {

    public static final String LOG_TAG = SelectPictureActivity.class.getSimpleName();

    public static final String FILE_PROVIDER_AUTHORITY = ".silicompressor.provider";
    private static final int REQUEST_TAKE_CAMERA_PHOTO = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE_VID = 2;
    private static final int REQUEST_TAKE_VIDEO = 200;
    private static final int TYPE_IMAGE = 1;
    private static final int TYPE_VIDEO = 2;

    String mCurrentPhotoPath;
    Uri capturedUri = null;
    Uri compressUri = null;
    ImageView imageView;
    TextView picDescription;
    private ImageView videoImageView;
    LinearLayout compressionMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_picture);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = findViewById(R.id.photo);
        videoImageView = findViewById(R.id.videoImageView);
        picDescription = findViewById(R.id.pic_description);
        compressionMsg = findViewById(R.id.compressionMsg);

        imageView.setOnClickListener(v -> requestPermissions(TYPE_IMAGE));

        videoImageView.setOnClickListener(view -> requestPermissions(TYPE_VIDEO));
    }

    /**
     * Request Permission for writing to External Storage in 6.0 and up
     */
    private void requestPermissions(int mediaType) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (mediaType == TYPE_IMAGE) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_STORAGE_VID);
            }

        } else {
            if (mediaType == TYPE_IMAGE) {
                // Want to compress an image
                dispatchTakePictureIntent();
            } else if (mediaType == TYPE_VIDEO) {
                // Want to compress a video
                dispatchTakeVideoIntent();
            }

        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(this, "You need to enable the permission for External Storage Write" +
                            " to test out this library.", Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE_VID: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakeVideoIntent();
                } else {
                    Toast.makeText(this, "You need to enable the permission for External Storage Write" +
                            " to test out this library.", Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            }
            default:
        }
    }

    private File createMediaFile(int type) throws IOException {

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = (type == TYPE_IMAGE) ? "JPEG_" + timeStamp + "_" : "VID_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(
                type == TYPE_IMAGE ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES);
        File file = File.createTempFile(
                fileName,  /* prefix */
                type == TYPE_IMAGE ? ".jpg" : ".mp4",         /* suffix */
                storageDir      /* directory */
        );

        // Get the path of the file created
        mCurrentPhotoPath = file.getAbsolutePath();
        Log.d(LOG_TAG, "mCurrentPhotoPath: " + mCurrentPhotoPath);
        return file;
    }

    private void dispatchTakePictureIntent() {
        /*Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");*/


        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createMediaFile(TYPE_IMAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                // Error occurred while creating the File
                Log.d(LOG_TAG, "Error occurred while creating the file");

            }
            // Continue only if the File was successfully created
            if (photoFile != null) {

                // Get the content URI for the image file
                capturedUri = FileProvider.getUriForFile(this,
                        getPackageName() + FILE_PROVIDER_AUTHORITY,
                        photoFile);

                Log.d(LOG_TAG, "Log1: " + capturedUri);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedUri);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_CAMERA_PHOTO);

            }
        }
    }


    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        takeVideoIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            try {

                takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);
                takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                capturedUri = FileProvider.getUriForFile(this,
                        getPackageName() + FILE_PROVIDER_AUTHORITY,
                        createMediaFile(TYPE_VIDEO));

                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedUri);
                Log.d(LOG_TAG, "VideoUri: " + capturedUri.toString());
                startActivityForResult(takeVideoIntent, REQUEST_TAKE_VIDEO);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }

    // Method which will process the captured image
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //verify if the image was gotten successfully
        if (requestCode == REQUEST_TAKE_CAMERA_PHOTO && resultCode == Activity.RESULT_OK) {

            new ImageCompressionAsyncTask(this).execute(capturedUri.toString(),
                    getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Silicompressor/images");


        } else if (requestCode == REQUEST_TAKE_VIDEO && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                //create destination directory
                File f = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/Silicompressor/videos");
                if (f.mkdirs() || f.isDirectory()) {
                    //compress and output new video specs
                    //new VideoCompressAsyncTask(this).execute("true", mCurrentPhotoPath, f.getPath());
                    new VideoCompressAsyncTask(this.getApplicationContext()).execute("false", capturedUri.toString(), f.getPath());
                }

            }
        }

    }

    class ImageCompressionAsyncTask extends AsyncTask<String, Void, String> {

        Context mContext;

        public ImageCompressionAsyncTask(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {

            return SiliCompressor.with(mContext).compress(params[0], new File(params[1]));

        }

        @Override
        protected void onPostExecute(String s) {

            float length = 0;
            String name;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                compressUri = Uri.parse(s);
                Cursor c = getContentResolver().query(compressUri, null, null, null, null);
                c.moveToFirst();
                name = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                length = c.getLong(c.getColumnIndex(OpenableColumns.SIZE)) / 1024;
            } else {
                File imageFile = new File(s);
                compressUri = Uri.fromFile(imageFile);
                name = imageFile.getName();
                length = imageFile.length() / 1024f; // Size in KB
            }

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), compressUri);
                imageView.setImageBitmap(bitmap);
                int compressWidth = bitmap.getWidth();
                int compressHieght = bitmap.getHeight();
                String text = String.format(Locale.US, "Name: %s\nSize: %fKB\nWidth: %d\nHeight: %d", name, length, compressWidth, compressHieght);
                picDescription.setVisibility(View.VISIBLE);
                picDescription.setText(text);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }


    class VideoCompressAsyncTask extends AsyncTask<String, String, String> {

        Context mContext;

        public VideoCompressAsyncTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            imageView.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_photo_camera_white_48px));
            compressionMsg.setVisibility(View.VISIBLE);
            picDescription.setVisibility(View.GONE);
        }

        @Override
        protected String doInBackground(String... paths) {
            String filePath = null;
            try {

                //This bellow is just a temporary solution to test that method call works
                boolean b = Boolean.parseBoolean(paths[0]);
                if (b) {
                    filePath = SiliCompressor.with(mContext).compressVideo(paths[1], paths[2]);
                } else {
                    Uri videoContentUri = Uri.parse(paths[1]);
                    // Example using the bitrate and video size parameters
                    /*filePath = SiliCompressor.with(mContext).compressVideo(
                            videoContentUri,
                            paths[2],
                            1280,
                            720,
                            1500000);*/
                    filePath = SiliCompressor.with(mContext).
                            setProgressListener(new ProgressListener() {
                                @Override
                                public void onProgress(float progress) {
                                    Log.i("Silicompressor", "COMPRESSION PROGRESS : " + (int)(progress * 100));
                                }

                                @Override
                                public void onComplete() {

                                }
                            }).
                            compressVideo(
                                    videoContentUri,
                                    paths[2]
                            );
                }


            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return filePath;

        }


        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);

            File imageFile = new File(compressedFilePath);
            compressUri = Uri.fromFile(imageFile);
            String name = imageFile.getName();
            float length = imageFile.length() / 1024f; // Size in KB

            String value = length + " KB";
            String text = String.format(Locale.US, "%s\nName: %s\nSize: %s", getString(R.string.video_compression_complete), name, value);
            compressionMsg.setVisibility(View.GONE);
            picDescription.setVisibility(View.VISIBLE);
            picDescription.setText(text);
            Log.i("Silicompressor", "Path: " + compressedFilePath);
        }
    }


}
