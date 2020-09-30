/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.technology.retrofit;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.retrofit.Retrofit2.BodySaverInterceptor;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import okio.Timeout;

/**
 * The local client for
 * {@link <a href="https://square.github.io/okhttp/">OkHttp3</a>} library. Intended for
 * data loading from resources, arrays, etc. Allows network delay emulation.
 *
 * @see #getContentRaw
 * @see #getContent
 * @see #handle(Request)
 */
public abstract class BaseLocalOkHttpClient2 extends OkHttpClient {

    /** The JSON mime type (the value is {@value}). */
    @SuppressWarnings("WeakerAccess")
    public    static final String       TYPE_JSON                   = "application/json";

    /** The default message {@code Response.Builder} message (the value is {@value}). */
    @SuppressWarnings("WeakerAccess")
    public    static final String       MESSAGE                     = "";

    private   static final byte[]       EMPTY_BYTES                 = new byte[0];

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final int          HTTP_CODE_OK                = 200;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final List<Interceptor>   mInterceptors               = new ArrayList<>();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Charset             mCharset;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final String              mMediaType;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "rawtypes"})
    protected final Retrofit2           mRetrofit2;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       int                 mEmulatedNetworkDelay;

    /**
     * Initialises a newly created {@code BaseLocalOkHttpClient2} object.
     */
    @SuppressWarnings("unused")
    public BaseLocalOkHttpClient2() {
        this(null);
    }

    /**
     * Initialises a newly created {@code BaseLocalOkHttpClient2} object.
     *
     * @param retrofit2
     *        The {@code Retrofit2} component (or null)
     */
    @SuppressWarnings({"WeakerAccess", "rawtypes"})
    public BaseLocalOkHttpClient2(final Retrofit2 retrofit2) {
        this(retrofit2, null, null);
    }

    /**
     * Initialises a newly created {@code BaseLocalOkHttpClient2} object.
     *
     * @param retrofit2
     *        The {@code Retrofit2} component (or null)
     *
     * @param mediaType
     *        The media type, e.g. "application/json" (the default one)
     *
     * @param charset
     *        The {@code Charset} component (or null) for getting bytes from string
     *
     * @see #getContent
     */
    @SuppressWarnings("WeakerAccess")
    public BaseLocalOkHttpClient2(@SuppressWarnings("rawtypes") final Retrofit2 retrofit2,
                                  final String mediaType, final Charset charset) {
        mMediaType  = mediaType;
        mCharset    = charset;

        mRetrofit2  = retrofit2;
        if (retrofit2 == null) return;

        final HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        add(logger);

        //noinspection unused
        add(new BodySaverInterceptor() {
            @Override
            public void set(BodyCache data) {
                mRetrofit2.setData(data);
            }
        });
    }

    /**
     * Subject to override, returns content as string. Called if {@link #getContentRaw}
     * return null, otherwise ignored.
     *
     * @return  The content to put in {@link Response} object
     *
     * @see #handle(Request)
     */
    @SuppressWarnings({"SameReturnValue", "WeakerAccess"})
    protected String getContent() {
        return null;
    }

    /**
     * Subject to override, returns content as byte array. If returns null,
     * {@link #getContent} will be called.
     *
     * @return  The content to put in {@link Response} object
     *
     * @see #handle(Request)
     */
    @SuppressWarnings({"SameReturnValue", "WeakerAccess"})
    protected byte[] getContentRaw() {
        return null;
    }

    /**
     * Helper method to get string array as JSON.
     *
     * @param data
     *        The string array
     *
     * @return  The JSON string
     */
    @SuppressWarnings("unused")
    public static String getJson(final String... data) {
        return data == null ? null: getJson(Arrays.asList(data));
    }

    /**
     * Helper method to get string collection as JSON.
     *
     * @param data
     *        The string collection
     *
     * @return  The JSON string
     */
    @SuppressWarnings("WeakerAccess")
    public static String getJson(final Collection<String> data) {
        if (data == null) return null;
        final StringBuilder builder = new StringBuilder("[");
        for (final String str: data)
            builder.append("{\"title\":\"").append(str).append("\"},");
        return builder.replace(builder.length() - 1, builder.length(), "]").toString();
    }

    /**
     * Sets network delay emulation.
     *
     * @param delay
     *        The emulated network delay (in seconds)
     *
     * @return  This {@code BaseLocalOkHttpClient2} object to allow for chaining of calls
     */
    @SuppressWarnings("unused")
    public BaseLocalOkHttpClient2 setEmulatedNetworkDelay(final int delay) {
        CoreLogger.logWarning("emulated network delay set to " + delay + " seconds");

        mEmulatedNetworkDelay = delay;
        return this;
    }

    /**
     * Adds {@code Interceptor}.
     *
     * @param interceptor
     *        The {@code Interceptor}
     *
     * @return  This {@code BaseLocalOkHttpClient2} object to allow for chaining of calls
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public BaseLocalOkHttpClient2 add(final Interceptor interceptor) {
        return handle(interceptor, true);
    }

    /**
     * Removes {@code Interceptor}.
     *
     * @param interceptor
     *        The {@code Interceptor}
     *
     * @return  This {@code BaseLocalOkHttpClient2} object to allow for chaining of calls
     */
    @SuppressWarnings("unused")
    public BaseLocalOkHttpClient2 remove(final Interceptor interceptor) {
        return handle(interceptor, false);
    }

    private BaseLocalOkHttpClient2 handle(final Interceptor interceptor, final boolean add) {
        if (interceptor == null)
            CoreLogger.logWarning("interceptor == null");
        else {
            final boolean result = add ? mInterceptors.add(interceptor): mInterceptors.remove(interceptor);
            if (!result)
                CoreLogger.logError("can't " + (add ? "add": "remove") + " interceptor " + interceptor);
        }
        return this;
    }

    /**
     * Emulates OkHttp3 request to the Internet, subject to override
     * (if customizing {@link #getContentRaw} is not enough).
     *
     * @param request
     *        The {@code Request}
     *
     * @return  The {@code Response}
     *
     * @throws  IOException
     *          please refer to the exception description
     */
    @SuppressWarnings({"RedundantThrows", "WeakerAccess"})
    protected Response handle(final Request request) throws IOException {
        byte[] content = getContentRaw();
        if (content == null) {
            final String str = getContent();
            if (str != null)
                content = mCharset == null ? str.getBytes(): str.getBytes(mCharset);
        }
        if (content == null) {
            CoreLogger.logError("no content, please use 'getContent()' or 'getContentRaw()' methods");
            content = EMPTY_BYTES;
        }
        return new Response.Builder()
                .code(HTTP_CODE_OK)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message(MESSAGE)
                .body(ResponseBody.create(MediaType.parse(
                        mMediaType != null ? mMediaType: TYPE_JSON), content))
                .build();
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public WebSocket newWebSocket(final Request request, @NonNull final WebSocketListener listener) {
        return new WebSocket() {
            @Override          public void    cancel   (                           ) {                 }
            @Override          public boolean close    (final int i, final String s) { return    true; }
            @Override          public long    queueSize(                           ) { return       0; }
            @Override @NonNull public Request request  (                           ) { return request; }
            @Override          public boolean send     (@NonNull final ByteString b) { return    true; }
            @Override          public boolean send     (@NonNull final String s    ) { return    true; }
        };
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public Call newCall(@NonNull final Request request) {
        return new CallI(request);
    }

    private class CallI implements Call {

        private final Request mRequest;

        @SuppressWarnings("unused")
        private CallI(final Request request) {
            mRequest = request;
        }

        @NonNull
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
                        CoreLogger.log("interceptor failed", exception);
                    }
            }
            return response;
        }

        @Override
        public void enqueue(@NonNull final Callback callback) {
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    new Thread() {
                        @Override
                        public void run() {
                            enqueueWrapper(callback);
                        }
                    }.start();
                }

                @NonNull
                @Override
                public String toString() {
                    return "BaseLocalOkHttpClient2 - Call.enqueueWrapper()";
                }
            };
            if (mEmulatedNetworkDelay <= 0)
                runnable.run();
            else
                new Handler(Looper.getMainLooper()).postDelayed(runnable, mEmulatedNetworkDelay * 1000);
        }

        private void enqueueWrapper(final Callback callback) {
            if (callback == null) {
                CoreLogger.logError("callback == null");
                return;
            }
            Utils.safeRun(new Runnable() {
                @Override
                public void run() {
                    try {
                        callback.onResponse(CallI.this, execute());
                    }
                    catch (IOException exception) {
                        callback.onFailure(CallI.this, exception);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "BaseLocalOkHttpClient2 - Callback.onResponse()";
                }
            });
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override @NonNull public Call    clone     () { return new CallI(mRequest); }
        @Override          public void    cancel    () {                             }
        @Override          public boolean isCanceled() { return               false; }
        @Override          public boolean isExecuted() { return               false; }
        @Override @NonNull public Request request   () { return            mRequest; }
        @Override @NonNull public Timeout timeout   () { return        Timeout.NONE; }

        private class ChainI implements Chain {

            private final Response mResponse;

            @SuppressWarnings("unused")
            private ChainI(final Response response) {
                mResponse = response;
            }

            @Nullable   // for application interceptors this is always null
            @Override public Connection connection          (                             ) { return       null; }
            @NonNull
            @Override public Call       call                (                             ) { return CallI.this; }
            @Override public int        connectTimeoutMillis(                             ) { return          0; }
            @NonNull
            @Override public Response   proceed             (@NonNull final Request r     ) { return  mResponse; }
            @Override public int        readTimeoutMillis   (                             ) { return          0; }
            @NonNull
            @Override public Request    request             (                             ) { return   mRequest; }
            @NonNull
            @Override public Chain      withConnectTimeout  (final int i, @NonNull final TimeUnit t)
                                                                                            { return       this; }
            @NonNull
            @Override public Chain      withReadTimeout     (final int i, @NonNull final TimeUnit t)
                                                                                            { return       this; }
            @NonNull
            @Override public Chain      withWriteTimeout    (final int i, @NonNull final TimeUnit t)
                                                                                            { return       this; }
            @Override public int        writeTimeoutMillis  (                             ) { return          0; }
        }
    }
}
