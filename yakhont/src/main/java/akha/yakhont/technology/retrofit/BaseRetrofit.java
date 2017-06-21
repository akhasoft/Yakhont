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

import android.support.annotation.CallSuper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

/**
 * The base component to work with {@link <a href="http://square.github.io/retrofit/">Retrofit</a>}.
 * <p>
 * Supports both {@link <a href="http://square.github.io/retrofit/1.x/retrofit/">Retrofit</a>}
 * and {@link <a href="http://square.github.io/retrofit/2.x/retrofit/">Retrofit 2</a>}.
 * <p>
 * For example, in Activity (or in Application):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.technology.retrofit.Retrofit2;
 *
 * private static Retrofit2&lt;MyRetrofitApi&gt; sRetrofit = new Retrofit2&lt;&gt;();
 *
 * &#064;Override
 * protected void onCreate(Bundle savedInstanceState) {
 *     super.onCreate(savedInstanceState);
 *     ...
 *     sRetrofit.init(MyRetrofitApi.class, "http://.../");
 * }
 *
 * public static Retrofit2&lt;MyRetrofitApi&gt; getRetrofit() {
 *     return sRetrofit;
 * }
 * </pre>
 *
 * Here the <code>MyRetrofitApi</code> may looks as follows:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import com.mypackage.model.MyData;
 *
 * import retrofit2.Call;
 * import retrofit2.http.GET;
 *
 * public interface MyRetrofitApi {
 *
 *     &#064;GET("/data")
 *     Call&lt;MyData[]&gt; data();
 * }
 * </pre>
 *
 * And the model class:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * package com.mypackage.model;
 *
 * import com.google.gson.annotations.SerializedName;
 *
 * public class MyData {
 *
 *     &#064;SerializedName("name")
 *     private String mName;
 *
 *     &#064;SerializedName("age")
 *     private int mAge;
 *
 *     ...
 * }
 * </pre>
 *
 * To prevent model from obfuscation please add the following line to the proguard configuration file:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * -keep class com.mypackage.model.** { *; }
 * </pre>
 *
 * @param <T>
 *        The Retrofit API type
 *
 * @param <B>
 *        The Retrofit builder type
 *
 * @see Retrofit
 * @see Retrofit2
 *
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
public abstract class BaseRetrofit<T, B> {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected T                                     mRetrofitApi;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected int                                   mConnectionTimeout;

    /**
     * Initialises a newly created {@code BaseRetrofit} object.
     */
    public BaseRetrofit() {
    }

    /**
     * Returns the Retrofit API defined by the service interface.
     *
     * @return  The Retrofit API
     */
    public T getApi() {
        return mRetrofitApi;
    }

    /**
     * Returns the connection timeout (in seconds).
     *
     * @return  The connection timeout
     */
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
    @CallSuper
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
     * @return  The Retrofit builder
     */
    @SuppressWarnings("unused")
    public abstract B getDefaultBuilder(@NonNull final String retrofitBase);
}
