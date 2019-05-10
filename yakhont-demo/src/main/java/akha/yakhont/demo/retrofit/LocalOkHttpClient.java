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

import akha.yakhont.demo.retrofit.LocalOkHttpClientHelper.Data;

import akha.yakhont.Core.Utils;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import retrofit.client.Client;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

/**
 * OkHttp3 local client, based on implementation of Matt Swanson
 * (please refer to https://gist.github.com/swanson/7dee3f3474e30fe8f15c)
 */
public class LocalOkHttpClient implements Client {

    private static final String             TAG                         = "LocalOkHttpClient";
    private static final int                HTTP_CODE_OK                = 200;

    private final LocalOkHttpClientHelper   mLocalOkHttpClientHelper;
    private int                             mEmulatedNetworkDelay;

    public LocalOkHttpClient(Context context) {
        mLocalOkHttpClientHelper = new LocalOkHttpClientHelper(context);
    }

    public LocalOkHttpClientHelper getLocalOkHttpClientHelper() {
        return mLocalOkHttpClientHelper;
    }

    @SuppressWarnings("UnusedReturnValue")
    public LocalOkHttpClient setEmulatedNetworkDelay(int delay) {
        mEmulatedNetworkDelay = delay;
        return this;
    }

    @Override
    public Response execute(Request request) throws IOException {
        final int delay = mEmulatedNetworkDelay;
        if (delay > 0) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            //noinspection Convert2Lambda,Anonymous2MethodRef
            Utils.runInBackground(delay * 1000, new Runnable() {
                @Override
                public void run() {
                    countDownLatch.countDown();
                }
            });
            try {
                countDownLatch.await();
            }
            catch (InterruptedException exception) {
                Log.e(TAG, "interrupted", exception);
            }
        }
        Data data = mLocalOkHttpClientHelper.execute(request.getUrl(), request.getMethod());
        return new Response(request.getUrl(), HTTP_CODE_OK, data.message(),
                new ArrayList<>(), new TypedInputStream(data));
    }

    private static class TypedInputStream implements TypedInput {

        private final Data mData;

        private TypedInputStream(Data data) {
            mData = data;
        }

        @Override
        public String mimeType() {
            return mData.mimeType();
        }

        @Override
        public long length() {
            try {
                return mData.stream().available();
            }
            catch (IOException exception) {
                Log.e(TAG, "error", exception);
                return 0;
            }
        }

        @Override
        public InputStream in() {
            return mData.stream();
        }
    }
}
