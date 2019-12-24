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

package akha.yakhont.technology.retrofit;

import akha.yakhont.Core;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.squareup.okhttp.OkHttpClient;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter.Builder;
import retrofit.RestAdapter.Log;
import retrofit.RestAdapter.LogLevel;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.client.Response;

import retrofit.YakhontRestAdapter;

/**
 * The component to work with
 * {@link <a href="http://square.github.io/retrofit/1.x/retrofit/">Retrofit</a>} 1.x APIs.
 *
 * <p>Every loader should have unique Retrofit object; don't share it with other loaders.
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
@SuppressWarnings("unused")
public class Retrofit<T, D> extends BaseRetrofit<T, Builder, Callback<D>, D> {

    private static final Callback                   EMPTY_CALLBACK          = new Callback() {
        @Override public void success(final Object object, final Response response) {}
        @Override public void failure(final RetrofitError error                   ) {}
    };

    private              YakhontRestAdapter<T>      mYakhontRestAdapter;
    private              String                     mData;

    /**
     * Initialises a newly created {@code Retrofit} object.
     */
    @SuppressWarnings("unused")
    public Retrofit() {
        CoreLogger.logWarning("please consider using Retrofit 2");
    }

    /**
     * Returns server response.
     *
     * @return  The server response
     */
    public String getData() {
        return mData;
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
        CoreReflection.invoke(mWrappedApi, method, getEmptyCallback());
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

        final Class<?>[] params = method.getParameterTypes();
        if (params.length == 0 || !Callback.class.isAssignableFrom(params[params.length - 1])) {
            CoreLogger.logError("unsupported (not accepts Callback) method " + method);
            return null;
        }

        if (args == null || args.length == 0) {
            if (params.length == 1) {
                CoreLogger.logWarning("missed callback argument for method " + method);
                return CoreReflection.invoke(proxy, method, callback);
            }
            else {
                CoreLogger.logError("no arguments for method " + method);
                return null;
            }
        }

        final Object last = args[args.length - 1];
        if (last == null) {
            CoreLogger.logError("parameter callback == null for method " + method);
            args[args.length - 1] = callback;
        }
        else if (!last.equals(callback))
            CoreLogger.logWarning("unexpected callback for method " + method);

        return CoreReflection.invoke(proxy, method, args);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public YakhontRestAdapter<T> getYakhontRestAdapter() {
        return mYakhontRestAdapter;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit<T, D> init(
            @NonNull final Class<T> service, @NonNull final String retrofitBase,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
            @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
            @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers) {
        return init(service, getDefaultBuilder(retrofitBase, headers),
                connectTimeout, readTimeout, true);
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("WeakerAccess")
    @Override
    public Retrofit<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder,
                               @IntRange(from = 1) final int connectTimeout,
                               @IntRange(from = 1) final int readTimeout,
                               final boolean makeOkHttpClient) {

        super.init(service, builder, connectTimeout, readTimeout, makeOkHttpClient);

        if (makeOkHttpClient) {
            final OkHttpClient okHttpClient = new OkHttpClient();
            okHttpClient.setConnectTimeout(Core.adjustTimeout(connectTimeout), TimeUnit.MILLISECONDS);
            okHttpClient.setReadTimeout   (Core.adjustTimeout(readTimeout   ), TimeUnit.MILLISECONDS);

            builder.setClient(new OkClient(okHttpClient));
        }

        mYakhontRestAdapter     = new YakhontRestAdapter<>();
        mOriginalApi            = mYakhontRestAdapter.create(service, builder.build());

        adjustApi(service);

        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit<T, D> init(@NonNull final Class<T> service, @NonNull final String retrofitBase) {
        super.init(service, retrofitBase);
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder) {
        super.init(service, builder);
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit<T, D> init(@NonNull final Class<T> service, @NonNull final Builder builder,
                               @IntRange(from = 1) final int connectTimeout,
                               @IntRange(from = 1) final int readTimeout) {
        super.init(service, builder, connectTimeout, readTimeout);
        return this;
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
     * @param headers
     *        The optional HTTP headers (or null)
     *
     * @return  The Retrofit Builder
     */
    public Builder getDefaultBuilder(@NonNull  final String                 retrofitBase,
                                     @Nullable final Map<String, String>    headers) {

        final Builder builder = new Builder()
                .setLog(new BodySaverLogger() {
                    @Override
                    public void set(String data) {
                        mData = data;
                    }
                })

                .setLogLevel(LogLevel.FULL)         // for BodySaverLogger
//              .setLogLevel(CoreLogger.isFullInfo() ? LogLevel.FULL: LogLevel.NONE)

                .setEndpoint(retrofitBase);

        if (headers != null && !headers.isEmpty())
            //noinspection Convert2Lambda
            builder.setRequestInterceptor(new RequestInterceptor() {
                @Override
                public void intercept(RequestFacade request) {
                    for (final Map.Entry<String, String> header: headers.entrySet())
                        request.addHeader(header.getKey(), header.getValue());
                }
            });

        return builder;
    }

    /**
     * Saves server response (can be retrieved via {@link #getData}).
     */
    public static abstract class BodySaverLogger implements Log {

        // well, it's a hack - but why not? For outdated technology which will never be updated...

        private String      mPrevMessage, mUrl;

        /**
         * Please refer to the base method description.
         */
        @Override
        public void log(final String message) {
            if (message != null) {
                if (message.endsWith("ms)")) {
                    final String tmp = message.substring(0, message.lastIndexOf(' '));
                    mUrl = tmp.substring(tmp.lastIndexOf(' ') + 1);
                }
                if (message.startsWith("<--- END HTTP (")) {
                    if (mUrl == null)
                        CoreLogger.logError("url == null for " + mPrevMessage);
                    else if (mPrevMessage == null)
                        CoreLogger.logError("previous message == null for " + mUrl);
                    else
                        set(mPrevMessage);

                    mUrl = null;
                }
                CoreLogger.log(message);
            }
            mPrevMessage = message;
        }

        /**
         * Saves server response.
         *
         * @param data
         *        The data to save
         */
        public abstract void set(final String data);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link LoaderRx} class to provide Retrofit support.
     *
     * @param <D>
     *        The type of data
     */
    public static class RetrofitRx<D> extends LoaderRx<Response, Exception, D> {

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         */
        public RetrofitRx() {
            super();
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         */
        public RetrofitRx(final boolean isRx2) {
            super(isRx2);
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx}
         *        either emits one value only or an error notification, {@code false} otherwise
         */
        public RetrofitRx(final boolean isRx2, final boolean isSingle) {
            super(isRx2, isSingle);
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         */
        public RetrofitRx(final CommonRx<BaseResponse<Response, Exception, D>> commonRx) {
            super(commonRx);
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
         */
        public RetrofitRx(final CommonRx<BaseResponse<Response, Exception, D>> commonRx, final boolean isSingle) {
            super(commonRx, isSingle);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public RetrofitRx<D> subscribe(final SubscriberRx<BaseResponse<Response, Exception, D>> subscriber) {
            return (RetrofitRx<D>) super.subscribe(subscriber);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public RetrofitRx<D> subscribeSimple(final SubscriberRx<D> subscriber) {
            return (RetrofitRx<D>) super.subscribeSimple(subscriber);
        }
    }
}
