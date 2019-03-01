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

import akha.yakhont.Core.Utils;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

/**
 * based on implementation of Matt Swanson
 * (please refer to https://gist.github.com/swanson/7dee3f3474e30fe8f15c)
 */
public class LocalOkHttpClient implements Client {

    private static final String             TAG                         = "LocalOkHttpClient";

    private final LocalOkHttpClientHelper   mLocalOkHttpClientHelper;

    public LocalOkHttpClient(Context context) {
        mLocalOkHttpClientHelper = new LocalOkHttpClientHelper(context);
    }

    public LocalOkHttpClientHelper getLocalOkHttpClientHelper() {
        return mLocalOkHttpClientHelper;
    }

    @Override
    public Response execute(Request request) throws IOException {
        final int delay = mLocalOkHttpClientHelper.getDelay();
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
        LocalOkHttpClientHelper.Data data = mLocalOkHttpClientHelper.execute(request.getUrl(),
                request.getMethod());
        //noinspection Convert2Diamond
        return new Response(request.getUrl(), LocalOkHttpClientHelper.HTTP_CODE_OK, data.message(),
                new ArrayList<Header>(), new TypedInputStream(data));
    }

    private static class TypedInputStream implements TypedInput {

        private final LocalOkHttpClientHelper.Data mData;

        private TypedInputStream(LocalOkHttpClientHelper.Data data) {
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
