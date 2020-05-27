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

import akha.yakhont.Core;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.FlavorHelper;
import akha.yakhont.FlavorHelper.FlavorCommonRx;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.rx.BaseRx;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;
import akha.yakhont.technology.rx.Rx3;
import akha.yakhont.technology.rx.Rx3.Rx3Disposable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

import okhttp3.ConnectionSpec;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.logging.HttpLoggingInterceptor;

import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;

import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.CallAdapter.Factory;
import retrofit2.Callback;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.Retrofit.Builder;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.adapter.rxjava2.Result;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * The component to work with
 * {@link <a href="https://square.github.io/retrofit/2.x/retrofit/">Retrofit</a>} 2.x APIs.
 *
 * <p>Every loader should have unique Retrofit2 object; don't share it with other loaders.
 *
 * @param <T>
 *        The type of Retrofit API
 *
 * @param <D>
 *        The type of data
 *
 * @see CoreLoad
 *
 * @author akha
 */
public class Retrofit2<T, D> extends BaseRetrofit<T, Builder, Callback<D>, D> {

    @SuppressWarnings("CharsetObjectCanBeUsed")
    private static final Charset                UTF8            = Charset.forName("UTF-8");

    private static final Callback               EMPTY_CALLBACK  = new Callback() {
        @Override public void onResponse(final Call call, final Response  response ) {}
        @Override public void onFailure (final Call call, final Throwable throwable) {}
    };

    private              BodyCache              mData;

    private final BodySaverInterceptor          mBodySaver      = new BodySaverInterceptor() {
        @Override
        public void set(final BodyCache data) {
            setData(data);
        }
    };
/*
    @SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef"})
    private static final Logger                 LOGGER          = new Logger() {
        @Override
        public void log(final String message) {
            CoreLogger.log(message);
        }
    };
*/
    private Class<T>                            mService;
    private Retrofit                            mRetrofit;

    private Runnable                            mCancelHandler;

    /**
     * Initialises a newly created {@code Retrofit2} object.
     */
    @SuppressWarnings("unused")
    public Retrofit2() {
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public void clearCall() {
        super.clearCall();

        setCancelHandler(null);
    }

    /**
     * Returns the data request canceling handler (if any).
     *
     * @return  The cancel handler
     */
    public Runnable getCancelHandler() {
        return mCancelHandler;
    }

    /**
     * Sets the data request canceling handler (mostly for raw calls).
     *
     * @param cancelHandler
     *        The cancel handler
     */
    @SuppressWarnings("WeakerAccess")
    public void setCancelHandler(final Runnable cancelHandler) {
        if (mCancelHandler != null && cancelHandler != null)
            CoreLogger.logWarning("about to redefine cancel handler " + mCancelHandler);
        mCancelHandler = cancelHandler;
    }

    /**
     * Returns the service interface.
     *
     * @return  The service interface
     */
    public Class<T> getService() {
        return mService;
    }

    /**
     * Returns Retrofit 2 component.
     *
     * @return  The Retrofit 2 component
     */
    public Retrofit getRetrofit() {
        return mRetrofit;
    }

    /**
     * Returns server response.
     *
     * @return  The server response
     */
    public BodyCache getData() {
        return mData;
    }

    /**
     * Sets server response.
     *
     * @param data
     *        The server response
     */
    @SuppressWarnings("WeakerAccess")
    public void setData(final BodyCache data) {
        mData = data;
    }

    /**
     * Returns empty Retrofit callback.
     *
     * @param <D>
     *        The type of data
     *
     * @return  The empty callback
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static <D> Callback<D> emptyCallback() {
        return (Callback<D>) EMPTY_CALLBACK;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected Callback<D> getEmptyCallback() {
        return emptyCallback();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected void checkForDefaultRequesterOnlyHandler(@NonNull final Method method)
            throws IllegalAccessException, InvocationTargetException, ExceptionInInitializerError {
        CoreReflection.invoke(mWrappedApi, method);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public <R, E> Object request(@NonNull final Method method, final Object[] args,
                                 final LoaderRx<R, E, D> rx) throws Throwable {
        final Callback<D> callback = getCallback();
        if (callback == null) {
            CoreLogger.logError("callback == null for method " + method);
            return null;
        }
        CoreLogger.log("about to handle method " + method);

        final T proxy = mOriginalApi;

        final Class<?> returnType = method.getReturnType();
        try {
            if (Call.class.isAssignableFrom(returnType)) {
                final Call<D> call = CoreReflection.invoke(proxy, method, args);
                if (call == null) throw new Exception("Call == null");

                setCancelHandler(new Runnable() {
                    @Override
                    public void run() {
                        call.cancel();
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Retrofit2 - Call.cancel()";
                    }
                });

                call.enqueue(callback);
                return call;
            }

            final CallbackRx<D> callbackRx = getRxWrapper(callback);

            final Object resultRx3 = Rx3.handle(proxy, method, args, callbackRx);
            if (resultRx3 != null) {

                setCancelHandler(new Runnable() {
                    @Override
                    public void run() {
                        Rx3.cancel(resultRx3);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Retrofit2 - Rx3.cancel()";
                    }
                });

                getRxDisposableHandler(rx).add(resultRx3);
                return resultRx3;
            }

            final Object resultRx = FlavorHelper.handleRx(proxy, method, args, callbackRx);
            if (resultRx != null) {

                setCancelHandler(new Runnable() {
                    @Override
                    public void run() {
                        FlavorHelper.cancelRx(resultRx);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Retrofit2 - FlavorHelper.cancelRx()";
                    }
                });

                FlavorCommonRx.add(rx, resultRx);
                return resultRx;
            }

            throw new Exception("unknown " + returnType + " (usually in Retrofit API)");
        }
        catch (InvocationTargetException exception) {
            CoreLogger.logError("please check your build.gradle: maybe " +
                    "\"implementation 'com.squareup.retrofit2:adapter-rxjava:...'\" and / or " +
                    "\"implementation 'com.squareup.retrofit2:adapter-rxjava2:...'\" are missing");
            throw exception;
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static <R, E, D> Rx3Disposable getRxDisposableHandler(final LoaderRx<R, E, D> rx) {
        checkRxComponent(rx);
        return rx == null ? CommonRx.getRxDisposableHandlerAnonymous(): rx.getRx().getRxDisposableHandler();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static <D> void checkRxComponent(final BaseRx<D> rx) {
        if (rx == null)
            CoreLogger.logWarning("Rx component was not defined, so anonymous handler will be used");
    }

    /**
     * Creates {@link CallbackRx} from {@link Callback}.
     *
     * @param callback
     *        The {@link Callback}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link CallbackRx}
     */
    public static <D> CallbackRx<D> getRxWrapper(final Callback<D> callback) {
        if (callback == null) CoreLogger.logError("callback == null");

        return callback == null ? null: new CallbackRx<D>() {
            @SuppressWarnings("unused")
            @Override
            public void onResult(final D result) {
                Utils.safeRun(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(null, Response.success(result));
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Retrofit2 - Callback.onResponse()";
                    }
                });
            }

            @SuppressWarnings("unused")
            @Override
            public void onError(final Throwable throwable) {
                Utils.safeRun(new Runnable() {
                                  @Override
                                  public void run() {
                                      callback.onFailure(null, throwable);
                                  }

                                  @NonNull
                                  @Override
                                  public String toString() {
                                      return "Retrofit2 - Callback.onFailure()";
                                  }
                });
            }
        };
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit2<T, D> init(
            @NonNull final Class<T> service, @NonNull final String retrofitBase,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers) {

        return init(service, retrofitBase, connectTimeout, readTimeout, headers, null);
    }

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param retrofitBase
     *        The Retrofit API endpoint URL
     *
     * @param connectTimeout
     *        The connection timeout (in seconds)
     *
     * @param readTimeout
     *        The read timeout (in seconds)
     *
     * @param headers
     *        The optional HTTP headers (or null)
     *
     * @param cookies
     *        The optional cookies (or null)
     *
     * @return  This {@code Retrofit2} object to allow for chaining of calls
     */
    @SuppressWarnings("WeakerAccess")
    public Retrofit2<T, D> init(
            @NonNull final Class<T> service, @NonNull final String retrofitBase,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> cookies) {

        return init(service, getDefaultBuilder(retrofitBase).client(getDefaultOkHttpClientBuilder(
                connectTimeout, readTimeout, readTimeout, headers, cookies).build()),
                connectTimeout, readTimeout, false);
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("WeakerAccess")
    @Override
    public Retrofit2<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder,
                                @IntRange(from = 1) final int connectTimeout,
                                @IntRange(from = 1) final int readTimeout,
                                final boolean makeOkHttpClient) {
        super.init(service, builder, connectTimeout, readTimeout, makeOkHttpClient);

        mService        = service;

        if (makeOkHttpClient) builder.client(getDefaultOkHttpClientBuilder(
                connectTimeout, readTimeout, readTimeout, null, null).build());

        mRetrofit       = builder.build();
        mOriginalApi    = mRetrofit.create(service);

        adjustApi(service);

        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit2<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder) {
        super.init(service, builder);
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("unused")
    @Override
    public Retrofit2<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder,
                                @IntRange(from = 1) final int connectTimeout,
                                @IntRange(from = 1) final int readTimeout) {
        super.init(service, builder, connectTimeout, readTimeout);
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit2<T, D> init(@NonNull final Class<T> service, @NonNull final String retrofitBase) {
        super.init(service, retrofitBase);
        return this;
    }

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param retrofitBase
     *        The Retrofit API endpoint URL
     *
     * @param client
     *        The {@code OkHttpClient}
     *
     * @return  This {@code Retrofit2} object to allow for chaining of calls
     */
    public Retrofit2<T, D> init(@NonNull final Class<T> service, @NonNull final String retrofitBase,
                                @NonNull final OkHttpClient client) {
        init(service, getDefaultBuilder(retrofitBase).client(client));
        return this;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Factory getFactoryRx(final Callable<Factory> callable) {
        try {
            return Utils.safeRun(callable);
        }
        catch (NoClassDefFoundError error) {    // normally it's ok
            CoreLogger.log(CoreLogger.getDefaultLevel(), "getFactory can't find class", error);
        }
        return null;
    }

    private void addFactory(final Builder builder, final Factory factory) {
        if (factory != null) builder.addCallAdapterFactory(factory);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Builder getDefaultBuilder(@NonNull final String retrofitBase) {
        return getDefaultBuilder(retrofitBase, null);
    }

    /**
     * Returns the default Retrofit builder.
     *
     * @param retrofitBase
     *        The service API endpoint URL
     *
     * @param factory
     *        The converter factory for serialization and deserialization of objects
     *
     * @return  The Retrofit builder
     */
    @SuppressWarnings("WeakerAccess")
    public Builder getDefaultBuilder(@NonNull final String retrofitBase,
                                     @SuppressWarnings("SameParameterValue") final Converter.Factory factory) {
        final Builder builder = new Builder()
                .addConverterFactory(factory != null ? factory: GsonConverterFactory.create())
                .baseUrl(retrofitBase);

        addFactory(builder, getFactoryRx(new Callable<Factory>() {
            @Override
            public Factory call() {
                return RxJava3CallAdapterFactoryTmp.createAsync();
            }

            @NonNull
            @Override
            public String toString() {
                return "RxJava3CallAdapterFactoryTmp.createAsync()";
            }
        }));
        addFactory(builder, getFactoryRx(new Callable<Factory>() {
            @Override
            public Factory call() {
                return RxJava2CallAdapterFactory.createAsync();
            }

            @NonNull
            @Override
            public String toString() {
                return "RxJava2CallAdapterFactory.createAsync()";
            }
        }));
        addFactory(builder, getFactoryRx(new Callable<Factory>() {
            @Override
            public Factory call() {
                return RxJavaCallAdapterFactory.createAsync();
            }

            @NonNull
            @Override
            public String toString() {
                return "RxJavaCallAdapterFactory.createAsync()";
            }
        }));

        return builder;
    }

    /**
     * Returns the default {@code OkHttpClient} builder.
     *
     * @return  The {@code OkHttpClient} builder
     */
    @SuppressWarnings("unused")
    public OkHttpClient.Builder getDefaultOkHttpClientBuilder() {
        return getDefaultOkHttpClientBuilder(Core.TIMEOUT_CONNECTION, Core.TIMEOUT_CONNECTION,
                Core.TIMEOUT_CONNECTION, null, null);
    }

    /**
     * Returns the default {@code OkHttpClient} builder.
     *
     * @param connectTimeout
     *        The connection timeout in seconds (<= 5 min) or milliseconds (> 5 min)
     *
     * @param readTimeout
     *        The read timeout in seconds (<= 5 min) or milliseconds (> 5 min)
     *
     * @param writeTimeout
     *        The write timeout in seconds (<= 5 min) or milliseconds (> 5 min)
     *
     * @param headers
     *        The optional HTTP headers (or null)
     *
     * @param cookies
     *        The optional cookies (or null)
     *
     * @return  The {@code OkHttpClient} builder
     */
    @SuppressWarnings("WeakerAccess")
    public OkHttpClient.Builder getDefaultOkHttpClientBuilder(
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int writeTimeout,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> cookies) {

        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Core.adjustTimeout(connectTimeout), TimeUnit.MILLISECONDS)
                .readTimeout   (Core.adjustTimeout(readTimeout   ), TimeUnit.MILLISECONDS)
                .writeTimeout  (Core.adjustTimeout(writeTimeout  ), TimeUnit.MILLISECONDS);

        builder.connectionSpecs(Arrays.asList(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT));

        final HttpLoggingInterceptor logger = new HttpLoggingInterceptor( /* LOGGER */ );
        logger.setLevel(CoreLogger.isFullInfo() ?
                HttpLoggingInterceptor.Level.BODY: HttpLoggingInterceptor.Level.NONE);
        builder.addInterceptor(logger);

        if (headers != null && !headers.isEmpty())
            //noinspection Convert2Lambda
            builder.addInterceptor(new Interceptor() {
                @Override
                public okhttp3.Response intercept(final Chain chain) throws IOException {
                    final Request.Builder requestBuilder = chain.request().newBuilder();

                    for (final Map.Entry<String, String> header: headers.entrySet())
                        requestBuilder.header(header.getKey(), header.getValue());

                    return chain.proceed(requestBuilder.build());
                }
            });

        builder.addInterceptor(mBodySaver);

        if (cookies != null && !cookies.isEmpty())
            builder.cookieJar(new CookieJar() {

                private final Map<HttpUrl, List<Cookie>> mCookieStore = Utils.newMap();

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    mCookieStore.put(url, cookies);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> list = mCookieStore.get(url);
                    //noinspection Convert2Diamond
                    list = list != null ? new ArrayList<>(list): new ArrayList<Cookie>();

                    for (final String name: cookies.keySet())
                        setCookie(name, cookies.get(name), list, url);

                    return list;
                }
            });

        return builder;
    }

    private void setCookie(final String name, final String value,
                           final List<Cookie> cookies, final HttpUrl url) {
        try {
            final Cookie cookie = createCookie(name, value, url);

            for (int i = cookies.size() - 1; i >= 0; i--)
                if (cookies.get(i).name().equalsIgnoreCase(name))
                    cookies.remove(i);

            cookies.add(cookie);
        }
        catch (Exception exception) {
            CoreLogger.log("setCookie failed", exception);
        }
    }

    /**
     * Creates a cookie.
     *
     * @param name
     *        The name
     *
     * @param value
     *        The value
     *
     * @param url
     *        The url
     **
     * @return  The cookie
     */
    @SuppressWarnings("WeakerAccess")
    protected Cookie createCookie(final String name, final String value, final HttpUrl url) {
        return new Cookie.Builder()
                .domain(url.host())
                .name(name)
                .value(value)
                .build();
    }

    /**
     * The server response (can be retrieved via {@link #getData}).
     */
    public static class BodyCache {

        private final String    mType;
        private final byte[]    mResponse;

        /**
         * Initialises a newly created {@code BodyCache} object.
         *
         * @param type
         *        The {@link MediaType}
         *
         * @param response
         *        The server response
         */
        private BodyCache(@NonNull final String type, @NonNull final byte[] response) {
            mType       = type;
            mResponse   = response;
        }

        /**
         * Returns media type.
         *
         * @return  The media type
         */
        public String getType() {
            return mType;
        }

        /**
         * Returns server response.
         *
         * @return  The server response
         */
        public byte[] getResponse() {
            return mResponse;
        }
    }

    /**
     * Saves server response (can be retrieved via {@link #getData}).
     */
    @SuppressWarnings("WeakerAccess")
    public static abstract class BodySaverInterceptor implements Interceptor {

        /**
         * Please refer to the base method description.
         */
        @Override
        public okhttp3.Response intercept(final Chain chain) throws IOException {
            final Request request = chain.request();
//          final String url = request.url().toString();

            final okhttp3.Response response = chain.proceed(request);
            final String url = getUrl(response);

            final String[] type = new String[1];
            final byte[][] data = new byte[1][];

            if (getResponse(response, url, type, data))
                set(new BodyCache(type[0], data[0]));

            return response;
        }

        /**
         * Saves server response.
         *
         * @param data
         *        The data to save
         */
        public abstract void set(final BodyCache data);
    }

    private static String getUrl(final okhttp3.Response response) {
        return response.request().url().toString();
    }

    /**
     * Returns server response as string.
     *
     * @param response
     *        The server response
     *
     * @return  The server response as string
     */
    @SuppressWarnings("unused")
    public static String getResponseString(final okhttp3.Response response) {
        final byte[][] data = new byte[1][];
        final String[] type = new String[1];

        if (!getResponseSafe(response, null, type, data)) return null;

        try {
            final MediaType mediaType = getMediaType(type[0]);
            final Charset charset = mediaType == null ? UTF8: mediaType.charset(UTF8);

            return charset == null ? null: new String(data[0], charset);
        }
        catch (Exception exception) {
            CoreLogger.log("getResponseString() failed", exception);
            return null;
        }
    }

    /**
     * Returns server response as byte array.
     *
     * @param response
     *        The server response
     *
     * @return  The server response as byte array
     */
    @SuppressWarnings("unused")
    public static byte[] getResponseBytes(final okhttp3.Response response) {
        final byte[][] data = new byte[1][];
        return getResponseSafe(response, null, null, data) ? data[0]: null;
    }

    /**
     * Returns media type of server response.
     *
     * @param response
     *        The server response
     *
     * @return  The media type
     */
    @SuppressWarnings("WeakerAccess")
    public static String getResponseType(final okhttp3.Response response) {
        final String[] type = new String[1];
        return getResponseSafe(response, null, type, null) ? type[0]: null;
    }

    /**
     * Returns media type of server response.
     *
     * @param response
     *        The server response
     *
     * @return  The media type
     */
    @SuppressWarnings("unused")
    public static MediaType getResponseMediaType(final okhttp3.Response response) {
        return getMediaType(getResponseType(response));
    }

    /**
     * Converts string to media type.
     *
     * @param type
     *        The media type to parse
     *
     * @return  The media type
     */
    public static MediaType getMediaType(final String type) {
        if (type == null || type.trim().length() == 0) {
            CoreLogger.logWarning("empty media type");
            return null;
        }
        try {
            final MediaType mediaType = MediaType.parse(type);
            if (mediaType == null)
                CoreLogger.logWarning("media type == null for " + type);
            return mediaType;
        }
        catch (Exception exception) {
            CoreLogger.log("getMediaType() failed", exception);
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean getResponseSafe(final okhttp3.Response response, final String url,
                                           final String[] type, final byte[][] data) {
        try {
            return getResponse(response, url, type, data);
        }
        catch (Exception exception) {
            CoreLogger.log("getResponse() failed", exception);
            return false;
        }
    }

    private static boolean getResponse(final okhttp3.Response response, String url,
                                       final String[] type, final byte[][] data) throws IOException {
        if (url == null && response != null) url = response.request().url().toString();

        if (response == null)             return logProblem(Level.ERROR, "response == null", url);
        if (data == null && type == null) return logProblem(Level.ERROR, "nothing to do"   , url);

        if (type != null && type.length != 1) return logProblem(Level.ERROR, "type length should be 1", url);
        if (data != null && data.length != 1) return logProblem(Level.ERROR, "data length should be 1", url);

        if (!HttpHeaders.hasBody(response)) return logProblem(Level.WARNING, "response has no body", url);

        final ResponseBody body = response.body();
        if (body == null) return logProblem(Level.WARNING, "response body == null", url);

        if (data != null) {
            final BufferedSource source = body.source();
            source.request(Long.MAX_VALUE);

            Buffer buffer = source.buffer();
            if ("gzip".equalsIgnoreCase(response.headers().get("Content-Encoding"))) {
                GzipSource gzip = null;
                //noinspection TryFinallyCanBeTryWithResources
                try {
                    gzip = new GzipSource(buffer.clone());
                    buffer = new Buffer();
                    buffer.writeAll(gzip);
                }
                finally {
                    if (gzip != null) gzip.close();
                }
            }
            data[0] = buffer.clone().readByteArray();
        }

        if (type != null) {
            final MediaType contentType = body.contentType();
            if (contentType == null) logProblem(Level.WARNING, "content type == null", url);
            type[0] = contentType == null ? null: contentType.toString();
        }

        return true;
    }

    @SuppressWarnings("SameReturnValue")
    private static boolean logProblem(final Level level, final String text, final String url) {
        CoreLogger.log(level, text + ", url " + url);
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class RxJava3CallAdapterFactoryTmp extends Factory {

        @Nullable
        private final Scheduler                                 mScheduler;
        private final boolean                                   mIsAsync;

        @SuppressWarnings("unused")
        public static RxJava3CallAdapterFactoryTmp create() {
            return new RxJava3CallAdapterFactoryTmp(null, false);
        }

        @SuppressWarnings("WeakerAccess")
        public static RxJava3CallAdapterFactoryTmp createAsync() {
            return new RxJava3CallAdapterFactoryTmp(null, true);
        }

        @SuppressWarnings("unused")
        public static RxJava3CallAdapterFactoryTmp createWithScheduler(final Scheduler scheduler) {
            if (scheduler == null) throw new NullPointerException("scheduler == null");
            return new RxJava3CallAdapterFactoryTmp(scheduler, false);
        }

        @SuppressWarnings("SameParameterValue")
        private RxJava3CallAdapterFactoryTmp(@Nullable final Scheduler scheduler, final boolean isAsync) {
            mScheduler  = scheduler;
            mIsAsync    = isAsync;
        }

        @Nullable
        @Override
        public CallAdapter<?, ?> get(final Type returnType, final Annotation[] annotations,
                                     final Retrofit retrofit) {
            final Class<?> rawType = getRawType(returnType);

            if (rawType == Completable.class) {
                // comment from Retrofit team:
                //   Completable is not parameterized (which is what the rest of this method deals with)
                //   so it can only be created with a single configuration.
                return new RxJava3CallAdapterTmp(Void.class, mScheduler, mIsAsync, false,
                        true, false, false, false, true);
            }

            final boolean isFlowable    = rawType == Flowable.class;
            final boolean isSingle      = rawType == Single.class;
            final boolean isMaybe       = rawType == Maybe.class;

            if (rawType != Observable.class && !isFlowable && !isSingle && !isMaybe) return null;

            boolean isResult = false;
            boolean isBody   = false;

            final Type responseType;
            if (!(returnType instanceof ParameterizedType)) {
                final String name = isFlowable ? "Flowable": isSingle ? "Single": isMaybe ? "Maybe": "Observable";
                throw new IllegalStateException(name + " return type must be parameterized"
                        + " as " + name + "<Foo> or " + name + "<? extends Foo>");
            }

            final Type observableType = getParameterUpperBound(0, (ParameterizedType) returnType);
            final Class<?> rawObservableType = getRawType(observableType);

            if (rawObservableType == Response.class) {
                if (!(observableType instanceof ParameterizedType))
                    throw new IllegalStateException("Response must be parameterized"
                            + " as Response<Foo> or Response<? extends Foo>");
                responseType = getParameterUpperBound(0, (ParameterizedType) observableType);
            }
            else if (rawObservableType == Result.class) {
                if (!(observableType instanceof ParameterizedType))
                    throw new IllegalStateException("Result must be parameterized"
                            + " as Result<Foo> or Result<? extends Foo>");
                responseType = getParameterUpperBound(0, (ParameterizedType) observableType);
                isResult = true;
            }
            else {
                responseType = observableType;
                isBody = true;
            }

            return new RxJava3CallAdapterTmp(responseType, mScheduler, mIsAsync, isResult, isBody,
                    isFlowable, isSingle, isMaybe, false);
        }
    }

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    private static class RxJava3CallAdapterTmp<R> implements CallAdapter<R, Object> {

        @Nullable
        private final Scheduler                                 mScheduler;
        private final Type                                      mResponseType;
        private final boolean                                   mIsAsync;
        private final boolean                                   mIsResult;
        private final boolean                                   mIsBody;
        private final boolean                                   mIsFlowable;
        private final boolean                                   mIsSingle;
        private final boolean                                   mIsMaybe;
        private final boolean                                   mIsCompletable;

        private RxJava3CallAdapterTmp(final Type responseType, final @Nullable Scheduler scheduler,
                                      final boolean isAsync, final boolean isResult, final boolean isBody,
                                      final boolean isFlowable, final boolean isSingle,
                                      final boolean isMaybe, final boolean isCompletable) {
            mResponseType   = responseType;
            mScheduler      = scheduler;
            mIsAsync        = isAsync;
            mIsResult       = isResult;
            mIsBody         = isBody;
            mIsFlowable     = isFlowable;
            mIsSingle       = isSingle;
            mIsMaybe        = isMaybe;
            mIsCompletable  = isCompletable;
        }

        @Override
        public Type responseType() {
            return mResponseType;
        }

        @Override
        public Object adapt(final Call<R> call) {
            final Observable<Response<R>> responseObservable = mIsAsync
                    ? new CallEnqueueObservableTmp<>(call)
                    : new CallExecuteObservableTmp<>(call);

            Observable<?> observable;
            if (mIsResult)
                observable = new ResultObservableTmp<>(responseObservable);
            else if (mIsBody)
                observable = new BodyObservableTmp<>(responseObservable);
            else
                observable = responseObservable;

            if (mScheduler != null)
                observable = observable.subscribeOn(mScheduler);

            if (mIsFlowable)
                return observable.toFlowable(BackpressureStrategy.LATEST);

            if (mIsSingle)
                return observable.singleOrError();
            if (mIsMaybe)
                return observable.singleElement();
            if (mIsCompletable)
                return observable.ignoreElements();

            return RxJavaPlugins.onAssembly(observable);
        }
    }

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    private static class CallExecuteObservableTmp<T> extends Observable<Response<T>> {

        private final Call<T>                                   mOriginalCall;

        private CallExecuteObservableTmp(final Call<T> originalCall) {
            mOriginalCall = originalCall;
        }

        @Override
        protected void subscribeActual(final Observer<? super Response<T>> observer) {
            // comment from Retrofit team:
            //   Since Call is a one-shot type, clone it for each new observer.
            final Call<T> call = mOriginalCall.clone();
            final CallDisposable disposable = new CallDisposable(call);

            observer.onSubscribe(disposable);
            if (disposable.isDisposed()) return;

            boolean terminated = false;
            try {
                Response<T> response = call.execute();

                if (!disposable.isDisposed())
                    observer.onNext(response);
                if (!disposable.isDisposed()) {
                    terminated = true;
                    observer.onComplete();
                }
            }
            catch (Throwable throwable) {
                handleAdapterThrowable(observer, throwable, terminated, disposable.isDisposed());
            }
        }

        private static final class CallDisposable implements Disposable {

            private final    Call<?>                            mCall;
            private volatile boolean                            mDisposed;

            private CallDisposable(final Call<?> call) {
                mCall = call;
            }

            @Override
            public void dispose() {
                mDisposed = true;
                mCall.cancel();
            }

            @Override
            public boolean isDisposed() {
                return mDisposed;
            }
        }
    }

    private static void handleAdapterThrowable(final Observer<?> observer, final Throwable throwable,
                                               final boolean terminated, final boolean disposed) {
        Exceptions.throwIfFatal(throwable);

        if      (terminated) RxJavaPlugins.onError(throwable);
        else if (!disposed ) handleAdapterThrowable(observer, throwable);
    }

    private static void handleAdapterThrowable(final Observer<?> observer, final Throwable throwable) {
        try {
            observer.onError(throwable);
        }
        catch (Throwable innerThrowable) {
            Exceptions.throwIfFatal(innerThrowable);
            RxJavaPlugins.onError(new CompositeException(throwable, innerThrowable));
        }
    }

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    private static class CallEnqueueObservableTmp<T> extends Observable<Response<T>> {

        private final Call<T>                                   mOriginalCall;

        private CallEnqueueObservableTmp(final Call<T> originalCall) {
            mOriginalCall = originalCall;
        }

        @Override
        protected void subscribeActual(final Observer<? super Response<T>> observer) {
            // comment from Retrofit team:
            //   Since Call is a one-shot type, clone it for each new observer.
            final Call<T> call = mOriginalCall.clone();
            final CallCallback<T> callback = new CallCallback<>(call, observer);
            observer.onSubscribe(callback);

            if (!callback.isDisposed()) call.enqueue(callback);
        }

        private static final class CallCallback<T> implements Disposable, Callback<T> {

            private final    Call<?>                            mCall;
            private final    Observer<? super Response<T>>      mObserver;
            private volatile boolean                            mDisposed;
            private          boolean                            mTerminated;

            private CallCallback(final Call<?> call, final Observer<? super Response<T>> observer) {
                mCall       = call;
                mObserver   = observer;
            }

            @Override
            public void onResponse(final Call<T> call, final Response<T> response) {
                if (mDisposed) return;

                try {
                    mObserver.onNext(response);

                    if (!mDisposed) {
                        mTerminated = true;
                        mObserver.onComplete();
                    }
                }
                catch (Throwable throwable) {
                    handleAdapterThrowable(mObserver, throwable, mTerminated, mDisposed);
                }
            }

            @Override
            public void onFailure(final Call<T> call, final Throwable throwable) {
                if (call.isCanceled()) return;
                handleAdapterThrowable(mObserver, throwable);
            }

            @Override
            public void dispose() {
                mDisposed = true;
                mCall.cancel();
            }

            @Override
            public boolean isDisposed() {
                return mDisposed;
            }
        }
    }

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    private static class BodyObservableTmp<T> extends Observable<T> {

        private final Observable<Response<T>>                   mUpStream;

        private BodyObservableTmp(final Observable<Response<T>> upStream) {
            mUpStream = upStream;
        }

        @Override
        protected void subscribeActual(final Observer<? super T> observer) {
            mUpStream.subscribe(new BodyObserver<>(observer));
        }

        private static class BodyObserver<R> implements Observer<Response<R>> {

            private final Observer<? super R>                   mObserver;
            private boolean                                     mTerminated;

            private BodyObserver(final Observer<? super R> observer) {
                mObserver = observer;
            }

            @Override
            public void onSubscribe(final Disposable disposable) {
                mObserver.onSubscribe(disposable);
            }

            @Override
            public void onNext(final Response<R> response) {
                if (response.isSuccessful())
                    mObserver.onNext(response.body());
                else {
                    mTerminated = true;

                    @SuppressWarnings("deprecation")
                    Throwable throwable = new retrofit2.adapter.rxjava2.HttpException(response);
                    handleAdapterThrowable(mObserver, throwable);
                }
            }

            @Override
            public void onComplete() {
                if (!mTerminated) mObserver.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                if (!mTerminated)
                    mObserver.onError(throwable);
                else {
                    // comment from Retrofit team:
                    //   This should never happen! onNext handles and forwards errors automatically.
                    Throwable broken = new AssertionError(
                            "This should never happen! Report as a bug with the full stacktrace.");
                    //noinspection UnnecessaryInitCause Two-arg AssertionError constructor is 1.7+ only.
                    broken.initCause(throwable);
                    RxJavaPlugins.onError(broken);
                }
            }
        }
    }

    // temp solution (quick hack of Retrofit sources) - waiting for official Retrofit RxJava3 support
    private static class ResultObservableTmp<T> extends Observable<Result<T>> {

        private final Observable<Response<T>>                   mUpStream;

        private ResultObservableTmp(final Observable<Response<T>> upStream) {
            mUpStream = upStream;
        }

        @Override
        protected void subscribeActual(final Observer<? super Result<T>> observer) {
            mUpStream.subscribe(new ResultObserver<>(observer));
        }

        private static class ResultObserver<R> implements Observer<Response<R>> {

            private final Observer<? super Result<R>>           mObserver;

            private ResultObserver(final Observer<? super Result<R>> observer) {
                mObserver = observer;
            }

            @Override
            public void onSubscribe(final Disposable disposable) {
                mObserver.onSubscribe(disposable);
            }

            @Override
            public void onNext(final Response<R> response) {
                mObserver.onNext(Result.response(response));
            }

            @Override
            public void onError(final Throwable throwable) {
                try {
                    mObserver.onNext(Result.error(throwable));
                }
                catch (Throwable innerThrowable) {
                    handleAdapterThrowable(mObserver, innerThrowable);
                    return;
                }
                mObserver.onComplete();
            }

            @Override
            public void onComplete() {
                mObserver.onComplete();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link LoaderRx} class to provide Retrofit 2 support.
     *
     * @param <D>
     *        The type of data
     */
    public static class Retrofit2Rx<D> extends LoaderRx<Response<D>, Throwable, D> {

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx() {
            super();
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param version
         *        The one of supported RxJava versions
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final RxVersions version) {
            super(version);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param version
         *        The one of supported RxJava versions
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx}
         *        either emits one value only or an error notification, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final RxVersions version, final boolean isSingle) {
            super(version, isSingle);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final CommonRx<BaseResponse<Response<D>, Throwable, D>> commonRx) {
            super(commonRx);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx}
         *        either emits one value only or an error notification, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final CommonRx<BaseResponse<Response<D>, Throwable, D>> commonRx, final boolean isSingle) {
            super(commonRx, isSingle);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Retrofit2Rx<D> subscribe(final SubscriberRx<BaseResponse<Response<D>, Throwable, D>> subscriber) {
            return (Retrofit2Rx<D>) super.subscribe(subscriber);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Retrofit2Rx<D> subscribeSimple(final SubscriberRx<D> subscriber) {
            return (Retrofit2Rx<D>) super.subscribeSimple(subscriber);
        }
    }
}
