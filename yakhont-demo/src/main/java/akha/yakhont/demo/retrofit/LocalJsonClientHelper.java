/*
 * Copyright (C) 2015-2017 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demo.retrofit;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

/**
 * based on implementation of Matt Swanson
 * (please refer to https://gist.github.com/swanson/7dee3f3474e30fe8f15c)
 */
public class LocalJsonClientHelper {

    public static final int HTTP_CODE_OK = 200;

    private final Context   mContext;
    private String          mScenario;
    private int             mEmulatedNetworkDelay;

    public LocalJsonClientHelper(Context context) {
        mContext = context;
    }

    public void setScenario(String scenario) {
        mScenario = scenario;
    }

    public void setEmulatedNetworkDelay(int delay) {
        mEmulatedNetworkDelay = delay;
    }

    public Data execute(String url, String method) throws IOException {
        URL requestedUrl = new URL(url);

        String prefix = "";
        if (mScenario != null) prefix = mScenario + "_";

        String fileName = (prefix + method + requestedUrl.getPath()).replace('/', '_');
        fileName = fileName.toLowerCase(Locale.getDefault());

        int resourceId = mContext.getResources().getIdentifier(fileName, "raw", mContext.getPackageName());

        if (resourceId == 0) {
            Log.e("LocalJsonClientHelper", "Could not find res/raw/" + fileName + ".json");
            throw new IOException("Could not find res/raw/" + fileName + ".json");
        }

        InputStream inputStream = mContext.getResources().openRawResource(resourceId);

        String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
        if (mimeType == null) mimeType = "application/json";

        if (mEmulatedNetworkDelay > 0) SystemClock.sleep(mEmulatedNetworkDelay);

        return new Data(mimeType, "Content from res/raw/" + fileName, inputStream);
    }

    public static class Data {

        private final String        mMimeType;
        private final String        mMessage;
        private final InputStream   mStream;

        private Data(String mimeType, String message, InputStream stream) {
            mMimeType   = mimeType;
            mMessage    = message;
            mStream     = stream;
        }

        public String mimeType() {
            return mMimeType;
        }

        public String message() {
            return mMessage;
        }

        public InputStream stream() {
            return mStream;
        }
    }
}
