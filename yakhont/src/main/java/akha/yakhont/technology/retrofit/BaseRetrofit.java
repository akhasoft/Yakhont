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

import akha.yakhont.Core;
import akha.yakhont.CoreLogger;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

// not completed
public abstract class BaseRetrofit<T, B> {

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected T                                     mRetrofitApi;
    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected int                                   mConnectionTimeout;

    /**
     * Initialises a newly created {@code BaseRetrofit} object.
     */
    public BaseRetrofit() {
    }

    /**
     * Returns the API defined by the service interface.
     *
     * @return  The API
     */
    public T getRetrofitApi() {
        return mRetrofitApi;
    }

    /**
     * Returns the connection timeout (in seconds).
     *
     * @return  The connection timeout
     */
    public int getConnectionTimeout() {
        return mConnectionTimeout;
    }

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param retrofitBase
     *        The Retrofit API endpoint URL
     */
    public void init(@NonNull final Class<T> service, @NonNull final String retrofitBase) {
        init(service, retrofitBase, Core.TIMEOUT_CONNECTION, Core.TIMEOUT_CONNECTION, null);
    }

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param builder
     *        The RetrofitBuilder
     */
    public void init(@NonNull final Class<T> service, @NonNull final B builder) {
        init(service, builder, Core.TIMEOUT_CONNECTION, Core.TIMEOUT_CONNECTION);
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
     */
    protected abstract void init(@NonNull final Class<T> service, @NonNull final String retrofitBase,
                                 @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int connectTimeout,
                                 @SuppressWarnings("SameParameterValue") @IntRange(from = 1) final int readTimeout,
                                 @SuppressWarnings("SameParameterValue") @Nullable final Map<String, String> headers);

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param builder
     *        The RetrofitBuilder
     *
     * @param connectTimeout
     *        The connection timeout (in seconds)
     *
     * @param readTimeout
     *        The read timeout (in seconds)
     */
    protected void init(@NonNull final Class<T> service, @NonNull final B builder,
                        @IntRange(from = 1) final int connectTimeout,
                        @IntRange(from = 1) final int readTimeout) {
        mConnectionTimeout = Math.max(connectTimeout, readTimeout);
        CoreLogger.log("connection timeout set to " + mConnectionTimeout +
                " seconds, read timeout set to " + readTimeout + " seconds");
    }

    /**
     * Returns the default Retrofit builder.
     *
     * @param retrofitBase
     *        The service API endpoint URL
     *
     * @return  The Retrofit Builder
     */
    public abstract B getDefaultBuilder(@NonNull final String retrofitBase);
}
