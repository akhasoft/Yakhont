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

import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.squareup.okhttp.OkHttpClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter.Builder;
import retrofit.RestAdapter.LogLevel;
import retrofit.YakhontRestAdapter;
import retrofit.android.AndroidLog;
import retrofit.client.OkClient;
import retrofit.client.Response;

/**
 * The component to work with
 * {@link <a href="http://square.github.io/retrofit/1.x/retrofit/">Retrofit</a>} 1.x APIs.
 *
 * @param <T>
 *        The type of Retrofit API
 *
 * @yakhont.see BaseResponseLoaderWrapper.CoreLoad
 *
 * @author akha
 */
@SuppressWarnings("unused")
public class Retrofit<T> extends BaseRetrofit<T, Builder> {

    private static final String                     LOG_RETROFIT_SUFFIX        = "-Retrofit";

    private YakhontRestAdapter<T>                   mYakhontRestAdapter;

    /**
     * Initialises a newly created {@code Retrofit} object.
     */
    @SuppressWarnings("unused")
    public Retrofit() {
        CoreLogger.logWarning("please consider using Retrofit 2");
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public YakhontRestAdapter<T> getYakhontRestAdapter() {
        return mYakhontRestAdapter;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void init(@NonNull final Class<T> service, @NonNull final String retrofitBase,
                     @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
                     @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
                     @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers) {

        final OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(connectTimeout, TimeUnit.SECONDS);
        okHttpClient.setReadTimeout   (readTimeout,    TimeUnit.SECONDS);

        init(service, getDefaultBuilder(retrofitBase, headers).setClient(new OkClient(okHttpClient)),
                connectTimeout, readTimeout);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void init(@NonNull final Class<T> service, @NonNull final Builder builder,
                     @IntRange(from = 1) final int connectTimeout,
                     @IntRange(from = 1) final int readTimeout) {

        super.init(service, builder, connectTimeout, readTimeout);

        mYakhontRestAdapter     = new YakhontRestAdapter<>();
        mRetrofitApi            = mYakhontRestAdapter.create(service, builder.build());
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
                .setLog(new AndroidLog(CoreLogger.getTag() + LOG_RETROFIT_SUFFIX))
                .setLogLevel(CoreLogger.isFullInfo() ? LogLevel.FULL: LogLevel.NONE)
                .setEndpoint(retrofitBase);

        if (headers != null && !headers.isEmpty())
            builder.setRequestInterceptor(new RequestInterceptor() {
                @Override
                public void intercept(RequestFacade request) {
                    for (final Map.Entry<String, String> header: headers.entrySet())
                        request.addHeader(header.getKey(), header.getValue());
                }
            });

        return builder;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link ValuesCacheAdapterWrapper} class to provide Retrofit support.
     *
     * @param <D>
     *        The type of data
     */
    public static class RetrofitAdapterWrapper<D> extends ValuesCacheAdapterWrapper<Response, Exception, D> {

        /**
         * Initialises a newly created {@code RetrofitAdapterWrapper} object. The data binding goes by default:
         * cursor's column {@link android.provider.BaseColumns#_ID _ID} binds to view with R.id._id,
         * column "title" - to R.id.title etc.
         *
         * @param context
         *        The Context
         *
         * @param layout
         *        The resource identifier of a layout file that defines the views
         */
        @SuppressWarnings("unused")
        public RetrofitAdapterWrapper(@NonNull final Context context, @LayoutRes final int layout) {
            super(context, layout);
        }

        /**
         * Initialises a newly created {@code RetrofitAdapterWrapper} object.
         *
         * @param context
         *        The Context
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
        public RetrofitAdapterWrapper(@NonNull final Context context, @LayoutRes final int layout,
                                      @NonNull @Size(min = 1) final String[] from,
                                      @NonNull @Size(min = 1) final    int[] to) {
            super(context, layout, from, to);
        }

        /**
         * Initialises a newly created {@code RetrofitAdapterWrapper} object.
         *
         * @param factory
         *        The BaseCacheAdapterFactory
         *
         * @param compatible
         *        The support flag for the BaseCacheAdapterFactory
         *
         * @param context
         *        The Context
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
        public RetrofitAdapterWrapper(@NonNull final BaseCacheAdapterFactory<ContentValues, Response, Exception, D> factory,
                                      @SuppressWarnings("SameParameterValue") final boolean compatible,
                                      @NonNull final Context context, @LayoutRes final int layout,
                                      @NonNull @Size(min = 1) final String[] from,
                                      @NonNull @Size(min = 1) final    int[] to) {
            super(factory, compatible, context, layout, from, to);
        }
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
         *        {@code true} if {@link akha.yakhont.technology.rx.BaseRx.CommonRx}
         *        either emits one value only or an error notification, {@code false} otherwise
         */
        public RetrofitRx(final boolean isRx2, final boolean isSingle) {
            super(isRx2, isSingle);
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param commonRx
         *        The {@link akha.yakhont.technology.rx.BaseRx.CommonRx} to use
         */
        public RetrofitRx(final CommonRx<BaseResponse<Response, Exception, D>> commonRx) {
            super(commonRx);
        }

        /**
         * Initialises a newly created {@code RetrofitRx} object.
         *
         * @param commonRx
         *        The {@link akha.yakhont.technology.rx.BaseRx.CommonRx} to use
         *
         * @param isSingle
         *        {@code true} if {@link akha.yakhont.technology.rx.BaseRx.CommonRx} either emits one value only or an error notification, {@code false} otherwise
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
