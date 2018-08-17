/*
 * Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demosimple.retrofit;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
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

import okio.ByteString;

public abstract class LocalJsonClient2Base extends OkHttpClient {

    private static final String         TAG                         = "LocalJsonClient2";

    private final List<Interceptor>     mInterceptors               = new ArrayList<>();
    private int                         mEmulatedNetworkDelay;

    protected abstract String getJson();

    @SuppressWarnings("unused")
    public LocalJsonClient2Base setEmulatedNetworkDelay(int delay) {
        mEmulatedNetworkDelay = delay;
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public LocalJsonClient2Base add(Interceptor interceptor) {
        return handle(interceptor, true);
    }

    @SuppressWarnings("unused")
    public LocalJsonClient2Base remove(Interceptor interceptor) {
        return handle(interceptor, false);
    }

    private LocalJsonClient2Base handle(Interceptor interceptor, boolean add) {
        if (interceptor == null)
            Log.w(TAG, "interceptor == null");
        else {
            boolean result = add ? mInterceptors.add(interceptor): mInterceptors.remove(interceptor);
            if (!result) Log.e(TAG, "can't " + (add ? "add": "remove") + " interceptor " + interceptor);
        }
        return this;
    }

    private Response handle(final Request request) {
        return new Response.Builder()
                .code(200)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message("")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                        getJson().getBytes()))
                .build();
    }

    private class CallI implements Call {

        private final Request mRequest;

        private CallI(final Request request) {
            mRequest = request;
        }

        @Override
        public Response execute() {
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
        public void enqueue(final Callback responseCallback) {
            @SuppressWarnings({"Anonymous2MethodRef", "Convert2Lambda"})
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    new Thread() {
                        @Override
                        public void run() {
                            enqueueWrapper(responseCallback);
                        }
                    }.start();
                }
            };
            if (mEmulatedNetworkDelay <= 0)
                runnable.run();
            else
                new Handler(Looper.getMainLooper()).postDelayed(runnable, mEmulatedNetworkDelay * 1000);
        }

        private void enqueueWrapper(Callback responseCallback) {
            try {
                responseCallback.onResponse(this, execute());
            }
            catch (IOException exception) {
                responseCallback.onFailure(this, exception);
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
