package com.blueeagle.textrecognition;

import android.graphics.Bitmap;

public class BitmapHelper {

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
            System.gc();
        }
    }
}
