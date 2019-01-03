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
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.retrofit.Retrofit2.BodySaverInterceptor;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Interceptor.Chain;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import okhttp3.logging.HttpLoggingInterceptor;

import okio.ByteString;

// for Retrofit 2
public class LocalJsonClient2 extends OkHttpClient {

    private static final String             TAG                         = "LocalJsonClient2";

    private final List<Interceptor>         mInterceptors               = new ArrayList<>();
    private final LocalJsonClientHelper     mLocalJsonClientHelper;

    private final Retrofit2                 mRetrofit2;

    public LocalJsonClient2(Context context, Retrofit2 retrofit2) {
        this(context, retrofit2, true);
    }

    @SuppressWarnings("WeakerAccess")
    public LocalJsonClient2(Context context, Retrofit2 retrofit2, boolean addInterceptors) {
        mLocalJsonClientHelper = new LocalJsonClientHelper(context);
        mRetrofit2 = retrofit2;
        if (!addInterceptors) return;

        final HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        add(logger);

        add(new BodySaverInterceptor() {
            @Override
            public void set(BodyCache data) {
                mRetrofit2.setData(data);
            }
        });
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public LocalJsonClient2 add(Interceptor interceptor) {
        return handle(interceptor, true);
    }

    @SuppressWarnings("unused")
    public LocalJsonClient2 remove(Interceptor interceptor) {
        return handle(interceptor, false);
    }

    private LocalJsonClient2 handle(Interceptor interceptor, boolean add) {
        if (interceptor == null)
            Log.w(TAG, "interceptor == null");
        else {
            boolean result = add ? mInterceptors.add(interceptor): mInterceptors.remove(interceptor);
            if (!result) Log.e(TAG, "can't " + (add ? "add": "remove") + " interceptor " + interceptor);
        }
        return this;
    }

    public LocalJsonClientHelper getLocalJsonClientHelper() {
        return mLocalJsonClientHelper;
    }

    private Response handle(final Request request) throws IOException {
        LocalJsonClientHelper.Data data = mLocalJsonClientHelper.execute(request.url().toString(),
                request.method());

        InputStream stream = data.stream();
        byte[] content = new byte[stream.available()];

        int result = stream.read(content);
        if (result <= 0) {
            Log.e(TAG, "can't read input stream");
            return null;
        }

        return new Response.Builder()
                .code(LocalJsonClientHelper.HTTP_CODE_OK)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message(data.message())
                .body(ResponseBody.create(MediaType.parse(data.mimeType()), content))
                .build();
    }

    private class CallI implements Call {

        private final Request mRequest;

        private CallI(final Request request) {
            mRequest = request;
        }

        @Override
        public Response execute() throws IOException {
            final Response response = handle(mRequest);
            if (mInterceptors.size() > 0) {
                final ChainI chain = new ChainI(response);
                for (final Interceptor interceptor: mInterceptors)
                    try {
                        interceptor.intercept(chain);
                    }
                    catch (Exception exception) {
                        Log.e(TAG, "interceptor failed", exception);
                    }
            }
            return response;
        }

        @Override
        public void enqueue(final Callback callback) {
            @SuppressWarnings("Convert2Lambda")
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    enqueueWrapper(callback);
                }
            };
            final int delay = mLocalJsonClientHelper.getDelay();
            if (delay > 0)
                Utils.runInBackground(delay * 1000, runnable);
            else
                Utils.runInBackground(runnable);
        }

        private void enqueueWrapper(Callback callback) {
            try {
                callback.onResponse(this, execute());
            }
            catch (IOException exception) {
                callback.onFailure(this, exception);
            }
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override public Call    clone     () { return new CallI(mRequest); }
        @Override public Request request   () { return            mRequest; }
        @Override public void    cancel    () {                             }
        @Override public boolean isExecuted() { return               false; }
        @Override public boolean isCanceled() { return               false; }

        private class ChainI implements Chain {

            private final Response mResponse;

            private ChainI(final Response response) {
                mResponse = response;
            }

            @Nullable   // for application interceptors this is always null
            @Override public Connection connection          (                 ) { return       null; }
            @Override public Call       call                (                 ) { return CallI.this; }
            @Override public Request    request             (                 ) { return   mRequest; }
            @Override public Response   proceed             (Request r        ) { return  mResponse; }
            @Override public int        connectTimeoutMillis(                 ) { return          0; }
            @Override public Chain      withConnectTimeout  (int i, TimeUnit t) { return       this; }
            @Override public int        readTimeoutMillis   (                 ) { return          0; }
            @Override public Chain      withReadTimeout     (int i, TimeUnit t) { return       this; }
            @Override public int        writeTimeoutMillis  (                 ) { return          0; }
            @Override public Chain      withWriteTimeout    (int i, TimeUnit t) { return       this; }
        }

    }

    @Override
    public Call newCall(final Request request) {
        return new CallI(request);
    }

    @Override
    public WebSocket newWebSocket(final Request request, WebSocketListener listener) {
        return new WebSocket() {
            @Override public Request request  (               ) { return request; }
            @Override public long    queueSize(               ) { return       0; }
            @Override public boolean send     (String s       ) { return    true; }
            @Override public boolean send     (ByteString b   ) { return    true; }
            @Override public boolean close    (int i, String s) { return    true; }
            @Override public void    cancel   (               ) {                 }
        };
    }
}
