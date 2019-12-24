/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.demo.gui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * not directly related to the Yakhont Demo - just some GUI stuff
 */
public class Utils {

    private Utils() {
    }

    @SuppressWarnings("WeakerAccess")
    public static int getDecodeBitmapInSampleSize(int height, int width, int reqHeight, int reqWidth) {
        int inSampleSize = 1;
        while (height / inSampleSize > reqHeight && width / inSampleSize > reqWidth)
            inSampleSize *= 2;
        return inSampleSize;
    }

    public static Bitmap decodeBitmap(Resources res, int resId, int reqHeight, int reqWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        options.inSampleSize = getDecodeBitmapInSampleSize(options.outHeight, options.outWidth, reqHeight, reqWidth);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }
}
