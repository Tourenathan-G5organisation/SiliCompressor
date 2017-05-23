package com.iceteck.silicompressor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.iceteck.silicompressorr.SiliCompressor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SelectPictureActivity extends AppCompatActivity {

    public static final String LOG_TAG = SelectPictureActivity.class.getSimpleName();

    private static final int REQUEST_TAKE_CAMERA_PHOTO = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;

    String mCurrentPhotoPath;
    Uri capturedUri = null;
    Uri compressUri = null;
    ImageView imageView;
    TextView picDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_picture);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = (ImageView) findViewById(R.id.photo);
        picDescription = (TextView) findViewById(R.id.pic_description);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions();
            }
        });
    }

    /**
     * Request Permission for writing to External Storage in 6.0 and up
     */
    private void requestPermissions(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
        }
        else{
            dispatchTakePictureIntent();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent();
                }
                else{
                    Toast.makeText(this, "You need enable the permission for External Storage Write" +
                            " to test out this library.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            default:
        }
    }

    private File createImageFile() throws IOException {

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        Log.d(LOG_TAG, "mCurrentPhotoPath: " + mCurrentPhotoPath);
        return image;
    }

    private void dispatchTakePictureIntent() {
        /*Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");*/


        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //takePictureIntent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File

            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                capturedUri = Uri.fromFile(photoFile);
                Log.d(LOG_TAG, "Log1: " + String.valueOf(capturedUri));
                Log.d(LOG_TAG, "Log2: "  + capturedUri.toString());

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedUri);

                startActivityForResult(takePictureIntent, REQUEST_TAKE_CAMERA_PHOTO);
                /*String title = getString(R.string.choose_a_pic);
                Intent chooserIntent = Intent.createChooser(intent, title);
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePictureIntent });
                startActivityForResult(chooserIntent, REQUEST_TAKE_CAMERA_PHOTO);*/
            }
        }
    }


    // Method which will process the captured image
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //verify if the image was gotten successfully
        if (requestCode == REQUEST_TAKE_CAMERA_PHOTO && resultCode == Activity.RESULT_OK) {

            try {
                if ((null == data) || (data.getData() == null)){
                    new ImageCompressionAsyncTask(this).execute(capturedUri.toString(),
                            Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+getPackageName()+"/media/images");
                }
                else {
                    new ImageCompressionAsyncTask(this).execute(data.toString(),);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    class ImageCompressionAsyncTask extends AsyncTask<String, Void, String>{

        Context mContext;

        public ImageCompressionAsyncTask(Context context){
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {

            String filePath = SiliCompressor.with(mContext).compress(params[0]);
            return filePath;


            /*
            Bitmap compressBitMap = null;
            try {
                compressBitMap = SiliCompressor.with(mContext).getCompressBitmap(params[0], true);
                return compressBitMap;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return compressBitMap;

            */
        }

        @Override
        protected void onPostExecute(String s) {
            /*
            if (null != s){
                imageView.setImageBitmap(s);
                int compressHieght = s.getHeight();
                int compressWidth = s.getWidth();
                float length = s.getByteCount() / 1024f; // Size in KB;

                String text = String.format("Name: %s\nSize: %fKB\nWidth: %d\nHeight: %d", "ff", length, compressWidth, compressHieght);
                picDescription.setVisibility(View.VISIBLE);
                picDescription.setText(text);
            }
            */

            File imageFile = new File(s);
            compressUri = Uri.fromFile(imageFile);

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), compressUri);
                imageView.setImageBitmap(bitmap);

                String name = imageFile.getName();
                float length = imageFile.length() / 1024f; // Size in KB
                int compressWidth = bitmap.getWidth();
                int compressHieght = bitmap.getHeight();
                String text = String.format(Locale.US, "Name: %s\nSize: %fKB\nWidth: %d\nHeight: %d", name, length, compressWidth, compressHieght);
                picDescription.setVisibility(View.VISIBLE);
                picDescription.setText(text);
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        }
    }


}
