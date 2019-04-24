/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
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
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

/**
 * OkHttp3 local client helper, based on implementation of Matt Swanson
 * (please refer to https://gist.github.com/swanson/7dee3f3474e30fe8f15c)
 */
public class LocalOkHttpClientHelper {

    public static final int                 HTTP_CODE_OK                = 200;

    private final WeakReference<Context>    mContext;
    private String                          mScenario;
    private int                             mEmulatedNetworkDelay;

    public LocalOkHttpClientHelper(Context context) {
        mContext = new WeakReference<>(context);
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

        Context context = mContext.get();
        if (context == null) handleError("context == null");

        //noinspection ConstantConditions
        int resourceId = context.getResources().getIdentifier(fileName,
                "raw", context.getPackageName());
        if (resourceId == 0) handleError("Could not find res/raw/" + fileName + ".json");

        InputStream inputStream = context.getResources().openRawResource(resourceId);

        String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
        if (mimeType == null) mimeType = "application/json";

        return new Data(mimeType, "Content from res/raw/" + fileName, inputStream);
    }

    private void handleError(String msg) throws IOException {
        Log.e("LocalOkHttpClientHelper", msg);
        throw new IOException(msg);
    }

    public int getDelay() {
        return mEmulatedNetworkDelay;
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
