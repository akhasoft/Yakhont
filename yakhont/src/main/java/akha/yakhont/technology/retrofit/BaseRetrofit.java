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
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.Requester;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * The base component to work with {@link <a href="https://square.github.io/retrofit/">Retrofit</a>}.
 * <p>
 * Supports both {@link <a href="https://square.github.io/retrofit/1.x/retrofit/">Retrofit</a>}
 * and {@link <a href="https://square.github.io/retrofit/2.x/retrofit/">Retrofit 2</a>}.
 *
 * <p>Every loader should have unique Retrofit2 (or Retrofit) object; don't share it with other loaders.
 *
 * <p>For example:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import com.yourpackage.model.YourData;
 * import com.yourpackage.retrofit.YourRetrofit;
 *
 * import akha.yakhont.technology.retrofit.Retrofit2;
 *
 * public static Retrofit2&lt;YourRetrofit, YourData[]&gt; getRetrofit() {
 *     // something like this
 *     return new Retrofit2&lt;YourRetrofit, YourData[]&gt;().init(
 *         YourRetrofit.class, "https://...");
 * }
 * </pre>
 *
 * Here the <code>YourRetrofit</code> may looks as follows:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * package com.yourpackage.retrofit;
 *
 * import com.yourpackage.model.YourData;
 *
 * import retrofit2.Call;
 * import retrofit2.http.GET;
 *
 * public interface YourRetrofit {
 *
 *     &#064;GET("/data")
 *     Call&lt;YourData[]&gt; getData();
 * }
 * </pre>
 *
 * And the model class:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * package com.yourpackage.model;
 *
 * import com.google.gson.annotations.SerializedName;
 *
 * public class YourData {
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
 * -keep class com.yourpackage.model.** { *; }
 * </pre>
 *
 * @param <T>
 *        The Retrofit API type
 *
 * @param <B>
 *        The Retrofit builder type
 *
 * @param <C>
 *        The Retrofit callback type
 *
 * @param <D>
 *        The type of the Retrofit callback data
 *
 * @see Retrofit2
 *
 * @author akha
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class BaseRetrofit<T, B, C, D> {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected int                                   mConnectionTimeout;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "RedundantSuppression" /* lint bug workaround */ })
    protected T                                     mOriginalApi, mWrappedApi;

    private final BaseHandler                       mBaseHandler    = new BaseHandler();

    private boolean                                 mFindMethod;
    private C                                       mCallback;

    /**
     * Initialises a newly created {@code BaseRetrofit} object.
     */
    protected BaseRetrofit() {
    }

    private boolean isRawCall() {
        return mCallback == null;
    }

    private class BaseHandler implements InvocationHandler {

        private Method                              mMethod;

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            if (isFindMethod()) {
                mMethod = method;
                return null;
            }
            try {
                if (isRawCall()) {
                    CoreLogger.log("raw Retrofit call for method " + method);
                    return CoreReflection.invoke(mOriginalApi, method, args);
                }
                else {
                    CoreLogger.log("handle Retrofit call for method " + method);
                    return request(method, args, null);
                }
            }
            catch (Throwable throwable) {
                CoreLogger.log(throwable);
                return null;
            }
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public abstract <R, E> Object request(@NonNull Method method, Object[] args,
                                          LoaderRx<R, E, D> rx) throws Throwable;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected C getCallback() {
        return mCallback;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected void adjustApi(@NonNull final Class<T> service) {
        mWrappedApi = createProxy(service, mBaseHandler);
    }

    @SuppressWarnings("unchecked")
    private T createProxy(@NonNull final Class<T> service, @NonNull final InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service}, handler);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected abstract C getEmptyCallback();

    @SuppressWarnings("unchecked")
    private void makeBogusRequest(@NonNull final Requester requester) {
        requester.makeRequest(getEmptyCallback());
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public Method getMethod(@NonNull final Requester requester) {
        mBaseHandler.mMethod = null;

        mFindMethod = true;
        try {
            makeBogusRequest(requester);
        }
        catch (RawCallException exception) {
            CoreLogger.log("can not find method 'cause of raw Retrofit call");
            return null;
        }
        finally {
            mFindMethod = false;
        }

        if (mBaseHandler.mMethod == null)
            CoreLogger.logError("mBaseHandler.mMethod == null");

        return mBaseHandler.mMethod;
    }

    /**
     * Returns the Retrofit API defined by the service interface.
     *
     * <p>Note: 'raw calls' means - without default Yakhont pre- and postprocessing.
     *
     * @param callback
     *        The loader's callback (or null for raw Retrofit calls)
     *
     * @return  The Retrofit API
     */
    public T getApi(final C callback) {
        if (callback == null && isFindMethod()) throw new RawCallException();
        set(callback);
        return mWrappedApi;
    }

    private void set(final C callback) {
        mCallback       = callback;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void clearCall() {
        mCallback       = null;
    }

    private boolean isFindMethod() {
        return mFindMethod;
    }

    @SuppressWarnings("unused")
    private static class RawCallException extends RuntimeException {}

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public boolean checkForDefaultRequesterOnly(@NonNull final Method method, @NonNull final C callback)
            throws IllegalAccessException, InvocationTargetException, ExceptionInInitializerError {
        final boolean findMethod = isFindMethod();
        if (findMethod)
            checkForDefaultRequesterOnlyHandler(method);
        else
            set(callback);
        return !findMethod;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected abstract void checkForDefaultRequesterOnlyHandler(@NonNull final Method method)
            throws IllegalAccessException, InvocationTargetException, ExceptionInInitializerError;

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
     * Returns the default Retrofit builder.
     *
     * @param retrofitBase
     *        The service API endpoint URL
     *
     * @return  The Retrofit builder
     */
    @SuppressWarnings("unused")
    public abstract B getDefaultBuilder(@NonNull final String retrofitBase);

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param retrofitBase
     *        The Retrofit API endpoint URL
     *
     * @return  This {@code BaseRetrofit} object to allow for chaining of calls
     */
    @SuppressWarnings("unused")
    public BaseRetrofit<T, B, C, D> init(@NonNull final Class<T> service, @NonNull final String retrofitBase) {
        return init(service, retrofitBase, Core.TIMEOUT_CONNECTION, Core.TIMEOUT_CONNECTION, null);
    }

    /**
     * Initialises Retrofit client.
     *
     * @param service
     *        The service interface
     *
     * @param builder
     *        The RetrofitBuilder
     *
     * @return  This {@code BaseRetrofit} object to allow for chaining of calls
     */
    @SuppressWarnings("unused")
    public BaseRetrofit<T, B, C, D> init(@NonNull final Class<T> service, @NonNull final B builder) {
        return init(service, builder, Core.TIMEOUT_CONNECTION, Core.TIMEOUT_CONNECTION, false);
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
     * @return  This {@code BaseRetrofit} object to allow for chaining of calls
     */
    protected abstract BaseRetrofit<T, B, C, D> init(
            @NonNull final Class<T> service, @NonNull final String retrofitBase,
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
     *
     * @return  This {@code BaseRetrofit} object to allow for chaining of calls
     */
    @SuppressWarnings("unused")
    public BaseRetrofit<T, B, C, D> init(@NonNull final Class<T> service, @NonNull final B builder,
                                         @IntRange(from = 1) final int connectTimeout,
                                         @IntRange(from = 1) final int readTimeout) {
        return init(service, builder, connectTimeout, readTimeout, true);
    }

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
     *
     * @param makeOkHttpClient
     *        {@code true} to create default OkHttpClient, {@code false} otherwise
     *
     * @return  This {@code BaseRetrofit} object to allow for chaining of calls
     */
    @CallSuper
    protected BaseRetrofit<T, B, C, D> init(@NonNull final Class<T> service, @NonNull final B builder,
                                            @IntRange(from = 1) final int connectTimeout,
                                            @IntRange(from = 1) final int readTimeout,
                                            final boolean makeOkHttpClient) {
        mConnectionTimeout = Math.max(connectTimeout, readTimeout);

        CoreLogger.log("connection timeout set to " + mConnectionTimeout +
                " seconds, read timeout set to " + readTimeout + " seconds, makeOkHttpClient == " + makeOkHttpClient);
        return this;
    }
}
