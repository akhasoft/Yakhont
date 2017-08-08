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

package akha.yakhont.technology.retrofit;

import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterFactory;
import akha.yakhont.adapter.ValuesCacheAdapterWrapper;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.app.Activity;
import android.content.ContentValues;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.Retrofit.Builder;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * The component to work with
 * {@link <a href="http://square.github.io/retrofit/2.x/retrofit/">Retrofit</a>} 2.x APIs.
 *
 * @param <T>
 *        The type of Retrofit API
 *
 * @yakhont.see BaseResponseLoaderWrapper.CoreLoad
 *
 * @author akha
 */
public class Retrofit2<T> extends BaseRetrofit<T, Builder> {

    private Class<T>                        mService;

    /**
     * Initialises a newly created {@code Retrofit2} object.
     */
    @SuppressWarnings("unused")
    public Retrofit2() {
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
     * Please refer to the base method description.
     */
    @Override
    public void init(@NonNull final Class<T> service, @NonNull final String retrofitBase,
                     @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
                     @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
                     @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers) {

        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout   (readTimeout,    TimeUnit.SECONDS);

        final HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(CoreLogger.isFullInfo() ?
                HttpLoggingInterceptor.Level.BODY:
                HttpLoggingInterceptor.Level.NONE);
        builder.addInterceptor(logger);

        if (headers != null && !headers.isEmpty())
            builder.addInterceptor(new Interceptor() {
                @Override
                public okhttp3.Response intercept(final Chain chain) throws IOException {
                    final Request.Builder requestBuilder = chain.request().newBuilder();

                    for (final Map.Entry<String, String> header: headers.entrySet())
                        requestBuilder.header(header.getKey(), header.getValue());

                    return chain.proceed(requestBuilder.build());
                }
            });

        init(service, getDefaultBuilder(retrofitBase).client(builder.build()),
                connectTimeout, readTimeout);
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("WeakerAccess")
    @Override
    public void init(@NonNull final Class<T> service, @NonNull final Builder builder,
                     @IntRange(from = 1) final int connectTimeout,
                     @IntRange(from = 1) final int readTimeout) {
        super.init(service, builder, connectTimeout, readTimeout);

        mService = service;
        final Retrofit retrofit = builder.build();
        mRetrofitApi = retrofit.create(service);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Builder getDefaultBuilder(@NonNull final String retrofitBase) {
        return new Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(retrofitBase);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link ValuesCacheAdapterWrapper} class to provide Retrofit 2 support.
     *
     * @param <D>
     *        The type of data
     */
    public static class Retrofit2AdapterWrapper<D> extends ValuesCacheAdapterWrapper<Response<D>, Throwable, D> {

        /**
         * Initialises a newly created {@code Retrofit2AdapterWrapper} object. The data binding goes by default:
         * cursor's column {@link android.provider.BaseColumns#_ID _ID} binds to view with R.id._id,
         * column "title" - to R.id.title etc.
         *
         * @param context
         *        The Activity
         *
         * @param layout
         *        The resource identifier of a layout file that defines the views
         */
        public Retrofit2AdapterWrapper(@NonNull final Activity context, @LayoutRes final int layout) {
            super(context, layout);
        }

        /**
         * Initialises a newly created {@code Retrofit2AdapterWrapper} object.
         *
         * @param context
         *        The Activity
         *
         * @param layout
         *        The resource identifier of a layout file that defines the views
         *
         * @param from
         *        The list of names representing the data to bind to the UI
         *
         * @param to
         *        The views that should display data in the "from" parameter
         */
        @SuppressWarnings("unused")
        public Retrofit2AdapterWrapper(@NonNull final Activity context, @LayoutRes final int layout,
                                       @NonNull @Size(min = 1) final String[] from,
                                       @NonNull @Size(min = 1) final    int[] to) {
            super(context, layout, from, to);
        }

        /**
         * Initialises a newly created {@code Retrofit2AdapterWrapper} object.
         *
         * @param factory
         *        The BaseCacheAdapterFactory
         *
         * @param compatible
         *        The support flag for the BaseCacheAdapterFactory
         *
         * @param context
         *        The Activity
         *
         * @param layout
         *        The resource identifier of a layout file that defines the views
         *
         * @param from
         *        The list of names representing the data to bind to the UI
         *
         * @param to
         *        The views that should display data in the "from" parameter
         */
        @SuppressWarnings("unused")
        public Retrofit2AdapterWrapper(@NonNull final BaseCacheAdapterFactory<ContentValues, Response<D>, Throwable, D> factory,
                                       @SuppressWarnings("SameParameterValue") final boolean compatible,
                                       @NonNull final Activity context, @LayoutRes final int layout,
                                       @NonNull @Size(min = 1) final String[] from,
                                       @NonNull @Size(min = 1) final    int[] to) {
            super(factory, compatible, context, layout, from, to);
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
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final boolean isRx2) {
            super(isRx2);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         *
         * @param isSingle
         *        {@code true} if {@link akha.yakhont.technology.rx.BaseRx.CommonRx}
         *        either emits one value only or an error notification, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final boolean isRx2, final boolean isSingle) {
            super(isRx2, isSingle);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param commonRx
         *        The {@link akha.yakhont.technology.rx.BaseRx.CommonRx} to use
         */
        @SuppressWarnings("unused")
        public Retrofit2Rx(final CommonRx<BaseResponse<Response<D>, Throwable, D>> commonRx) {
            super(commonRx);
        }

        /**
         * Initialises a newly created {@code Retrofit2Rx} object.
         *
         * @param commonRx
         *        The {@link akha.yakhont.technology.rx.BaseRx.CommonRx} to use
         *
         * @param isSingle
         *        {@code true} if {@link akha.yakhont.technology.rx.BaseRx.CommonRx} either emits one value only or an error notification, {@code false} otherwise
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
