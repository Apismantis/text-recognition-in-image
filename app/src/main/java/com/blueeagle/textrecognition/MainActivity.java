package com.blueeagle.textrecognition;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton imbGallery, imbCamera;
    private TextView tvDetectedText;
    private ImageView imvPhoto;

    static String TAG = "MainActivity";
    static final int RC_HANDLE_ALL_PERM = 1;
    static boolean RS_PERM = false;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_PICK_IMAGE = 2;

    private String mCurrentPhotoPath;
    private Bitmap imageBitmap;

    private TextRecognizer textRecognizer;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init view
        initView();

        // Check permission
        checkPermissions();

        imbGallery.setOnClickListener(this);
        imbCamera.setOnClickListener(this);

        textRecognizer = new TextRecognizer.Builder(this).build();
    }

    public void initView() {
        imbCamera = (ImageButton) findViewById(R.id.imbCamera);
        imbGallery = (ImageButton) findViewById(R.id.imbGallery);
        tvDetectedText = (TextView) findViewById(R.id.tvDetectedText);
        imvPhoto = (ImageView) findViewById(R.id.imvPhoto);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check permission
        RS_PERM = checkPermissions();
    }

    // Check all permission
    public boolean checkPermissions() {
        String[] requestPermission = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE};

        List<String> permissionNeedRequest = new ArrayList<>();
        int rs;

        for (String permission : requestPermission) {
            rs = ActivityCompat.checkSelfPermission(this, permission);
            if (rs != PackageManager.PERMISSION_GRANTED)
                permissionNeedRequest.add(permission);
        }

        if (!permissionNeedRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionNeedRequest.toArray(new String[permissionNeedRequest.size()]),
                    RC_HANDLE_ALL_PERM);

            Log.d(TAG, "All permission is not granted...");
            return false;
        }

        return true;
    }

    // Handle result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        switch (requestCode) {
            case RC_HANDLE_ALL_PERM:
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "All permission is granted.");
                    RS_PERM = true;
                } else {
                    RS_PERM = false;
                }

                break;

            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        if (!RS_PERM && (viewId == R.id.imbCamera || viewId == R.id.imbGallery)) {
            Toast.makeText(this,
                    "Text Recognition has no permission!. Please grant permission in " +
                            "Settings -> Application -> Text Recognition -> Permissions",
                    Toast.LENGTH_LONG).show();
        }

        Log.d(TAG, RS_PERM + "");

        switch (viewId) {
            case R.id.imbCamera:
                if (RS_PERM)
                    takePhoto();

                break;

            case R.id.imbGallery:
                if (RS_PERM)
                    pickImageFromGallery();

                break;
        }
    }

    /**
     * Detect text inside an image
     *
     * @param imageBitmap: Bitmap image contain text
     */
    public void detectTextInImage(Bitmap imageBitmap) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait! Detecting...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new DetectAsyncTask().execute(imageBitmap);
    }

    // Pick an image from gallery
    public void pickImageFromGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickPhotoIntent.setType("image/*");
        startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
    }

    // Open camera then take a photo
    public void takePhoto() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = createImageFile();

                if (photoFile != null) {
                    Uri photoUri = FileProvider.getUriForFile(
                            this,
                            "com.example.android.fileprovider",
                            photoFile);

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Can't create photo file. " + ex.getMessage());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    BitmapHelper.recycleBitmap(imageBitmap);

                    imageBitmap = ExifUtil.rotateBitmap(
                            mCurrentPhotoPath,
                            DecoderBitmap.decodeBitmap(mCurrentPhotoPath, imvPhoto.getWidth(), imvPhoto.getHeight()));

                    if (imageBitmap != null) {
                        imvPhoto.setImageBitmap(imageBitmap);
                        detectTextInImage(imageBitmap);
                    }
                }
                break;

            case REQUEST_PICK_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    BitmapHelper.recycleBitmap(imageBitmap);

                    imageBitmap = DecoderBitmap.scaleBitmap(
                            DecoderBitmap.readBitmap(this, uri, imvPhoto.getWidth(), imvPhoto.getHeight()),
                            imvPhoto.getWidth(),
                            imvPhoto.getHeight());

                    imvPhoto.setImageBitmap(imageBitmap);
                    detectTextInImage(imageBitmap);
                }
                break;
        }
    }

    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storgeDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storgeDir);

        mCurrentPhotoPath = image.getAbsolutePath();
        Log.d(TAG, "Current photo path: " + mCurrentPhotoPath);
        return image;
    }

    class DetectAsyncTask extends AsyncTask<Bitmap, String, SparseArray<TextBlock>> {

        @Override
        protected SparseArray<TextBlock> doInBackground(Bitmap... params) {
            // Check textRecognizer is operational
            if (!textRecognizer.isOperational()) {
                showToast("Detector is not available. Connect to the internet to download library");
                Log.w(TAG, "Detector dependencies are not yet available.");

                // Check for low storage.  If there is low storage, the native library will not be
                // downloaded, so detection will not become operational.
                IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
                boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

                if (hasLowStorage) {
                    showToast("Low Storage");
                    Log.w(TAG, "Low Storage");
                }

                return null;
            }

            Frame imageFrame = new Frame.Builder()
                    .setBitmap(params[0])
                    .build();

            // Detect text in image
            try {
                return textRecognizer.detect(imageFrame);
            } catch (Exception ex) {
                showToast("Detect failed. Please try again!");
                ex.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            //TODO: .......
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            //TODO: update progress
        }

        @Override
        protected void onPostExecute(SparseArray<TextBlock> result) {
            // Hide progress dialog
            progressDialog.setCancelable(true);
            progressDialog.dismiss();

            // Get text into detectedText string
            String detectedText = "";

            if (result != null) {
                for (int i = 0; i < result.size(); i++) {
                    TextBlock textBlock = result.get(result.keyAt(i));
                    detectedText += textBlock.getValue() + "\n\n";
                }
            }

            if (detectedText.equals(""))
                detectedText = "No text is detected";

            tvDetectedText.setText(detectedText);
        }

        public void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy...");

        BitmapHelper.recycleBitmap(imageBitmap);

        // Release bitmap from imvPhoto
        imvPhoto.setImageDrawable(null);

        super.onDestroy();
    }
}
