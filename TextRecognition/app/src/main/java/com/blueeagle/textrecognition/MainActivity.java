package com.blueeagle.textrecognition;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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
import java.security.Permission;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton imbGallery, imbCamera;
    private TextView tvDetectedText;
    private ImageView imvPhoto;

    static String TAG = "MainActivity";
    static final int RC_HANDLE_CAMERA_PERM = 2;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_PICK_IMAGE = 2;

    String mCurrentPhotoPath;

    private TextRecognizer textRecognizer;

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

    public void checkPermissions() {
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc != PackageManager.PERMISSION_GRANTED)
            requestCameraPermission();
    }

    public void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission...");

        final String[] cameraPermission = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, cameraPermission, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Request camera permission onClick",
                        Toast.LENGTH_LONG).show();

                ActivityCompat.requestPermissions(thisActivity, cameraPermission, RC_HANDLE_CAMERA_PERM);
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission is granted.");
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        switch (viewId) {
            case R.id.imbCamera:
                dispatchTakePictureIntent();
                break;

            case R.id.imbGallery:
                pickImageFromGallery();
                break;
        }
    }

    public void detectTextInImage(Bitmap imageBitmap) {
        if (!textRecognizer.isOperational()) {
            // Note: The first time that an app using a Vision API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any text,
            // barcodes, or faces.
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Low Storage", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Low Storage");
            }
        }

        Frame imageFrame = new Frame.Builder()
                .setBitmap(imageBitmap)
                .build();

        String detectedText = "";
        SparseArray<TextBlock> items = textRecognizer.detect(imageFrame);
        Log.d(TAG, "Detect: " + items.size() + " block");
        for (int i = 0; i < items.size(); i++) {
            TextBlock textBlock = items.get(items.keyAt(i));
            detectedText += textBlock.getValue();
        }

        if (detectedText.equals(""))
            detectedText = "No text is detected";

        Log.d(TAG, detectedText);
        tvDetectedText.setText(detectedText);
    }

    public void pickImageFromGallery() {
        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.setType("image/*");

        Intent pickIntent = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(getIntent, "Select an photo");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});

        startActivityForResult(chooserIntent, REQUEST_PICK_IMAGE);
    }

    public void dispatchTakePictureIntent() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Can't create photo file. " + ex.getMessage());
            }

            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Decoding bitmap.....");
                    Bitmap bitmap = decodeBitmap();
                    if (bitmap != null) {
                        imvPhoto.setImageBitmap(bitmap);
                        detectTextInImage(bitmap);
                        Log.d(TAG, "Decoded bitmap successful!");
                    }
                }
                break;

            case REQUEST_PICK_IMAGE:
                if (resultCode != RESULT_OK)
                    break;

                Uri uri = data.getData();

                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    imvPhoto.setImageBitmap(bitmap);
                    detectTextInImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
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
        return image;
    }

    public Bitmap decodeBitmap() {
        int targetW = imvPhoto.getWidth();
        int targetH = imvPhoto.getHeight();

        BitmapFactory.Options bmpOption = new BitmapFactory.Options();
        bmpOption.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);

        int photoW = bmpOption.outWidth;
        int photoH = bmpOption.outHeight;

        // Caculate scale ratio
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the view
        bmpOption.inJustDecodeBounds = false;
        bmpOption.inSampleSize = scaleFactor;
        bmpOption.inPurgeable = true;

        return BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);
    }
}
