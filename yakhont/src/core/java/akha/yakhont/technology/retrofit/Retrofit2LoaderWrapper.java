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

package akha.yakhont.technology.retrofit;

import akha.yakhont.Core;
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils.TypeHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.BaseLoader.CoreLoadExtendedBuilder;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.ConverterHelper;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;
import akha.yakhont.technology.rx.Rx.RxSubscription;
import akha.yakhont.technology.rx.Rx2.Rx2Disposable;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Extends the {@link BaseResponseLoaderWrapper} class to provide Retrofit 2 support.
 *
 * @param <D>
 *        The type of data
 *
 * @param <T>
 *        The type of Retrofit 2 API
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class Retrofit2LoaderWrapper<D, T> extends BaseResponseLoaderWrapper<Callback<D>, Response<D>, Throwable, D> {

    private static final   Annotation[]                     EMPTY_ANNOTATIONS   = new Annotation[] {};

    private final          Retrofit2<T, D>                  mRetrofit;

    /**
     * Initialises a newly created {@code Retrofit2LoaderWrapper} object.
     *
     * @param context
     *        The context
     *
     * @param fragment
     *        The fragment
     *
     * @param requester
     *        The requester
     *
     * @param table
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     *
     * @param retrofit
     *        The Retrofit2 component
     */
    @SuppressWarnings("unused")
    public Retrofit2LoaderWrapper(@NonNull final Context         context,
                                  @NonNull final Fragment        fragment,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String          table, final String description,
                                  @NonNull final Retrofit2<T, D> retrofit) {
        //noinspection RedundantTypeArguments
        this(context, fragment, null, requester, Core.TIMEOUT_CONNECTION, table, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver(), retrofit);
    }

    /**
     * Initialises a newly created {@code Retrofit2LoaderWrapper} object.
     *
     * @param context
     *        The context
     *
     * @param fragment
     *        The fragment
     *
     * @param loaderId
     *        The loader ID
     *
     * @param requester
     *        The requester
     *
     * @param timeout
     *        The timeout (in seconds)
     *
     * @param table
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     *
     * @param converter
     *        The converter
     *
     * @param uriResolver
     *        The URI resolver
     *
     * @param retrofit
     *        The Retrofit2 component
     */
    @SuppressWarnings("WeakerAccess")
    public Retrofit2LoaderWrapper(@NonNull final Context         context,
                                  @NonNull final Fragment        fragment, final Integer loaderId,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @IntRange(from = 1) final int  timeout,
                                  @NonNull final String          table, final String description,
                                  @NonNull final Converter<D>    converter,
                                  @NonNull final UriResolver     uriResolver,
                                  @NonNull final Retrofit2<T, D> retrofit) {
        super(context, fragment, loaderId, requester, table, description, converter, uriResolver);

        mRetrofit = retrofit;

        //noinspection Convert2Lambda
        mConverter.setConverterGetter(new BaseResponse.ConverterGetter<D>() {
            @Override
            public ConverterHelper<D> get(Type type) {
                if (type == null) type = getType();
                return type == null ? null: new ConverterHelperRetrofit2<>(getConverter(type));
            }
        });

        final List<BaseLoader<Callback<D>, Response<D>, Throwable, D>> baseLoaders =
                new ArrayList<>(1);

        setLoaderParameters(baseLoaders, timeout, new Callback<D>() {
            @Override
            public void onResponse(Call<D> call, Response<D> response) {
                onSuccess(call, response, baseLoaders.get(0));
            }

            @Override
            public void onFailure(Call<D> call, Throwable throwable) {
                onError(call, null, throwable, baseLoaders.get(0));
            }
        });
    }

    private void onError(@SuppressWarnings("UnusedParameters") final Call<D> call,
                         final Response<D> response, final Throwable error,
                         final BaseLoader<Callback<D>, Response<D>, Throwable, D> loader) {
        //noinspection Convert2Diamond
        loader.callbackHelper(false, new BaseResponse<Response<D>, Throwable, D>(
                null, response, null, error, Source.NETWORK, null));
    }

    private void onSuccess(final Call<D> call, final Response<D> response,
                           final BaseLoader<Callback<D>, Response<D>, Throwable, D> loader) {
        if (response.isSuccessful()) {
            final D body = response.body();
            final BaseResponse<Response<D>, Throwable, D> baseResponse = new BaseResponse<>(
                    body, response, null, null, Source.NETWORK, null);

            if (body != null) {
                final Class<?> type = body.getClass();  // collections are without generic info
                setTypeIfNotSet(TypeHelper.getType(type));

                final ContentValues contentValues = getDataForCache(type);
                if (contentValues != null)
                    baseResponse.setValues(new ContentValues[] {contentValues});
            }
            else
                CoreLogger.logError("body == null");

            loader.callbackHelper(true, baseResponse);
            return;
        }

        final ResponseBody errorBody = response.errorBody();
        try {
            final String error = errorBody == null ? null: errorBody.string();
            CoreLogger.logError("error body: " + error);
        }
        catch (IOException exception) {
            CoreLogger.logError("error body decoding exception: " + exception);
        }
        catch (OutOfMemoryError error) {
            CoreLogger.logError("error body is extremely large: " + error);
        }

        final int code = response.code();
        onError(call, response, new Exception("error code " + code), loader);
    }

    private ContentValues getDataForCache(@NonNull final Class type) {
        final BodyCache data = mRetrofit.getData();
        if (data == null)
            CoreLogger.logError("no data to cache found; if you're using your own " +
                    "OkHttpClient, please consider to add BodySaverInterceptor "        +
                    "(for working example please refer to LocalJsonClient2 in demo-simple)");
        return data == null ? null: mConverter.getValues(data.getType(), data.getResponse(), type);
    }

    private retrofit2.Converter<ResponseBody, D> getConverter(final Type type) {
        if (type == null) {
            CoreLogger.logError("type == null");
            return null;
        }
        try {
            final retrofit2.Converter<ResponseBody, D> converter =
                    mRetrofit.getRetrofit().responseBodyConverter(type, EMPTY_ANNOTATIONS);
            if (converter == null) CoreLogger.logError("can't find converter for type " + type);
            return converter;
        }
        catch (Exception exception) {
            CoreLogger.log("no converter for type " + type, exception);
            return null;
        }
    }

    private static class ConverterHelperRetrofit2<D> implements ConverterHelper<D> {

        private final retrofit2.Converter<ResponseBody, D>  mConverter;

        private ConverterHelperRetrofit2(final retrofit2.Converter<ResponseBody, D> converter) {
            mConverter = converter;
        }

        @Override
        public D get(final String mediaType, final byte[] data) {
            if (data == null || data.length == 0) {
                CoreLogger.logError("empty data");
                return null;
            }
            if (mConverter == null) {
                CoreLogger.logError("converter == null");
                return null;
            }

            try {
                final ResponseBody body = ResponseBody.create(Retrofit2.getMediaType(mediaType), data);
                final D result = mConverter.convert(body);
                if (result == null) CoreLogger.logError("result == null");
                return result;
            }
            catch (Exception exception) {
                CoreLogger.log("failed", exception);
                return null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link BaseResponseLoaderWrapper} objects. Creates the Retrofit2-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit 2 API
     */
    public static class Retrofit2LoaderBuilder<D, T> extends BaseResponseLoaderExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        private final Retrofit2<T, D>                       mRetrofit;
        private final LoaderRx<Response<D>, Throwable, D>   mRx;

        /**
         * Initialises a newly created {@code Retrofit2LoaderBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param retrofit
         *        The Retrofit2 component
         *
         * @param rx
         *        The Rx component
         */
        @SuppressWarnings("unused")
        public Retrofit2LoaderBuilder(@NonNull final Fragment                   fragment,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(fragment);

            mRetrofit = retrofit;
            mRx       = rx;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        public Requester<Callback<D>> getDefaultRequester() {
            return getRequester(new RequesterHelper<Callback<D>, T>(mType) {

                @SuppressWarnings("unused")
                @Override
                public void init() {
                    mMethod = findDefaultMethod(mType);
                }

                @SuppressWarnings("unused")
                @Override
                public void request(Callback<D> callback) throws Exception {
                    if (mRetrofit.checkForDefaultRequesterOnly(mMethod, callback))
                        mRetrofit.request(mMethod, null, mRx);
                }
            });
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        @Override
        protected Retrofit2LoaderWrapper<D, T> createLoaderWrapper() {
            final Method method = mRetrofit.getMethod(getRequester());

            final Retrofit2LoaderWrapper<D, T> result = new Retrofit2LoaderWrapper<>(getContext(),
                    getFragment(), mLoaderId, getRequester(), getTimeout(), getTableName(method),
                    mDescription, getConverter(), getUriResolver(), mRetrofit);

            setType(result, TypeHelper.getType(method));
            return result;
        }

        private Method findDefaultMethod(final Type type) {
            if (type == null) {
                CoreLogger.logError("type == null");
                return null;
            }
            for (final Method method: mRetrofit.getService().getMethods()) {
                final Class<?>[] params = method.getParameterTypes();
                if ((params == null || params.length == 0) &&
                        TypeHelper.checkType(type, TypeHelper.getType(method)))
                    return method;
            }
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@yakhont.link BaseResponseLoaderWrapper.CoreLoad} objects. Creates the Retrofit2-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit 2 API
     */
    public static class Retrofit2CoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        /**
         * Please refer to the base class description.
         */
        @SuppressWarnings("unused")
        public static abstract class LoaderCallback<D> extends BaseLoader.LoaderCallback<Callback<D>, Response<D>, Throwable, D> {
        }

        private final Retrofit2<T, D>                        mRetrofit;

        /**
         * Initialises a newly created {@code Retrofit2CoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param retrofit
         *        The Retrofit2 component
         */
        @SuppressWarnings("unused")
        public Retrofit2CoreLoadBuilder(@NonNull final Fragment fragment,
                                        @NonNull final Retrofit2<T, D> retrofit) {
            super(fragment);
            mRetrofit = retrofit;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public T getApi(final Callback<D> callback) {
            return mRetrofit.getApi(callback);
        }

        /**
         * Returns the {@link Rx2Disposable} component.
         *
         * @return  The {@link Rx2Disposable}
         */
        @SuppressWarnings("unused")
        public Rx2Disposable getRx2DisposableHandler() {
            return Retrofit2.getRx2DisposableHandler(mRx);
        }

        /**
         * Returns the {@link RxSubscription} component.
         *
         * @return  The {@link RxSubscription}
         */
        @SuppressWarnings("unused")
        public RxSubscription getRxSubscriptionHandler() {
            return Retrofit2.getRxSubscriptionHandler(mRx);
        }

        /**
         * Please refer to {@link Retrofit2#getRxWrapper}.
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public static <D> CallbackRx<D> getRxWrapper(final Callback<D> callback) {
            return Retrofit2.getRxWrapper(callback);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public CoreLoad create() {
            final Retrofit2LoaderBuilder<D, T> builder = new Retrofit2LoaderBuilder<>(
                    mFragment.get(), mRetrofit, mRx);
            if (mType != null) builder.setType(mType);
            return create(builder);
        }
    }
}
