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

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

// not completed
public class Retrofit2<T> extends BaseRetrofit<T, Retrofit.Builder> {

    /**
     * Initialises a newly created {@code Retrofit2} object.
     */
    public Retrofit2() {
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
                public Response intercept(final Chain chain) throws IOException {
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
    @Override
    public void init(@NonNull final Class<T> service, @NonNull final Retrofit.Builder builder,
                     @IntRange(from = 1) final int connectTimeout,
                     @IntRange(from = 1) final int readTimeout) {
        super.init(service, builder, connectTimeout, readTimeout);

        final Retrofit retrofit = builder.build();
        mRetrofitApi = retrofit.create(service);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Retrofit.Builder getDefaultBuilder(@NonNull final String retrofitBase) {
        return new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(retrofitBase);
    }
}
