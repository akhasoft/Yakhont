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
import akha.yakhont.technology.retrofit.Retrofit;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.AndroidException;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedString;

/**
 * Extends the {@link BaseResponseLoaderWrapper} class to provide Retrofit support.
 *
 * @param <D>
 *        The type of data
 *
 * @param <T>
 *        The type of Retrofit API
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class RetrofitLoaderWrapper<D, T> extends BaseResponseLoaderWrapper<Callback<D>, Response, Exception, D> {

    private final          Retrofit<T, D>                    mRetrofit;

    /**
     * Initialises a newly created {@code RetrofitLoaderWrapper} object.
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
     *        The Retrofit component
     */
    @SuppressWarnings("unused")
    public RetrofitLoaderWrapper(@NonNull final Context        context,
                                 @NonNull final Fragment       fragment,
                                 @NonNull final Requester<Callback<D>> requester,
                                 @NonNull final String         table, final String description,
                                 @NonNull final Retrofit<T, D> retrofit) {
        //noinspection RedundantTypeArguments
        this(context, fragment, null, requester, Core.TIMEOUT_CONNECTION, table, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver(), retrofit);
    }

    /**
     * Initialises a newly created {@code RetrofitLoaderWrapper} object.
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
     *        The Retrofit component
     */
    @SuppressWarnings("WeakerAccess")
    public RetrofitLoaderWrapper(@NonNull final Context        context,
                                 @NonNull final Fragment       fragment, final Integer loaderId,
                                 @NonNull final Requester<Callback<D>> requester,
                                 @IntRange(from = 1) final int timeout,
                                 @NonNull final String         table, final String description,
                                 @NonNull final Converter<D>   converter,
                                 @NonNull final UriResolver    uriResolver,
                                 @NonNull final Retrofit<T, D> retrofit) {
        super(context, fragment, loaderId, requester, table, description, converter, uriResolver);

        mRetrofit = retrofit;

        //noinspection Convert2Lambda
        mConverter.setConverterGetter(new BaseResponse.ConverterGetter<D>() {
            @Override
            public ConverterHelper<D> get(Type type) {
                if (type == null) type = getType();
                return type == null ? null: new ConverterHelperRetrofit<>(
                        mRetrofit.getYakhontRestAdapter().getConverter(), type);
            }
        });

        final List<BaseLoader<Callback<D>, Response, Exception, D>> baseLoaders = new ArrayList<>(1);

        setLoaderParameters(baseLoaders, timeout, new Callback<D>() {
            @Override
            public void success(final D result, final Response response) {
                onSuccess(result, response, baseLoaders.get(0));
            }

            @Override
            public void failure(final RetrofitError error) {
                onError(error, baseLoaders.get(0));
            }
        });
    }

    private void onError(final RetrofitError error, final BaseLoader<Callback<D>, Response, Exception, D> loader) {
        //noinspection Convert2Diamond
        loader.callbackHelper(false, new BaseResponse<Response, Exception, D>(
                null, null, null, new RetrofitException(error), Source.NETWORK, error));
    }

    private void onSuccess(final D result, final Response response,
                           final BaseLoader<Callback<D>, Response, Exception, D> loader) {
        final BaseResponse<Response, Exception, D> baseResponse = new BaseResponse<>(
                result, response, null, null, Source.NETWORK, null);

        if (result == null )
            CoreLogger.logError("result == null");
        else {
            final Class<?> type = result.getClass();  // collections are without generic info
            setTypeIfNotSet(TypeHelper.getType(type));

            if (response.getBody() != null) {
                final ContentValues contentValues = getDataForCache(type);
                if (contentValues != null)
                    baseResponse.setValues(new ContentValues[] {contentValues});
            }
            else
                CoreLogger.logError("body == null");
        }
        loader.callbackHelper(true, baseResponse);
    }

    private ContentValues getDataForCache(@NonNull final Class type) {
        final String data = mRetrofit.getData();
        if (data == null)
            CoreLogger.logError("no data to cache found; if you're using your own " +
                    "RestAdapter, please consider to add BodySaverLogger "              +
                    "(for working example please refer to akha.yakhont.technology.retrofit.Retrofit)");
        return data == null ? null: mConverter.getValues(data, null, type);
    }

    private static class ConverterHelperRetrofit<D> implements ConverterHelper<D> {

        private final retrofit.converter.Converter                  mConverter;
        private final Type                                          mType;

        private ConverterHelperRetrofit(@NonNull final retrofit.converter.Converter converter,
                                        @NonNull final Type type) {
            mConverter  = converter;
            mType       = type;
        }

        @Override
        public D get(final String data, final byte[] notUsed) {
            if (data == null || data.length() == 0) {
                CoreLogger.logError("empty data");
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                final D result = (D) mConverter.fromBody(new TypedString(data), mType);
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
     * Builder class for {@link BaseResponseLoaderWrapper} objects. Creates the Retrofit-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit API
     */
    public static class RetrofitLoaderBuilder<D, T> extends BaseResponseLoaderExtendedBuilder<Callback<D>, Response, Exception, D, T> {

        private final Retrofit<T, D>                                 mRetrofit;

        /**
         * Initialises a newly created {@code RetrofitLoaderBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitLoaderBuilder(@NonNull final Fragment fragment,
                                     @NonNull final Retrofit<T, D> retrofit) {
            super(fragment);
            mRetrofit = retrofit;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        public Requester<Callback<D>> getDefaultRequester() {
            return getRequester(new RequesterHelper<Callback<D>, T>(mType) {

                @SuppressWarnings("unused")
                @Override
                public void init() {
                    if (mType == null)
                        CoreLogger.logError("mType == null");
                    else
                        mMethod = mRetrofit.getYakhontRestAdapter().findDefaultMethod(mType);
                }

                @SuppressWarnings("unused")
                @Override
                public void request(@NonNull final Callback<D> callback) throws Exception {
                    if (mRetrofit.checkForDefaultRequesterOnly(mMethod, callback))
                        mRetrofit.request(mMethod, null, null);
                }
            });
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        @Override
        protected RetrofitLoaderWrapper<D, T> createLoaderWrapper() {
            final Method method = mRetrofit.getMethod(getRequester());

            final RetrofitLoaderWrapper<D, T> result = new RetrofitLoaderWrapper<>(getContext(),
                    getFragment(), mLoaderId, getRequester(), getTimeout(),
                    getTableName(method), mDescription, getConverter(), getUriResolver(), mRetrofit);

            setType(result, mRetrofit.getYakhontRestAdapter().getType(method));
            return result;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@yakhont.link BaseResponseLoaderWrapper.CoreLoad} objects. Creates the Retrofit-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit API
     */
    public static class RetrofitCoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response, Exception, D, T> {

        /**
         * Please refer to the base class description.
         */
        @SuppressWarnings("unused")
        public static abstract class LoaderCallback<D> extends BaseLoader.LoaderCallback<Callback<D>, Response, Exception, D> {
        }

        private final Retrofit<T, D>                                 mRetrofit;

        /**
         * Initialises a newly created {@code RetrofitCoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitCoreLoadBuilder(@NonNull final Fragment fragment,
                                       @NonNull final Retrofit<T, D> retrofit) {
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
         * Please refer to the base method description.
         */
        @Override
        public CoreLoad create() {
            final RetrofitLoaderBuilder<D, T> builder = new RetrofitLoaderBuilder<>(
                    mFragment.get(), mRetrofit);
            if (mType != null) builder.setType(mType);
            return create(builder);
        }
    }
}

class RetrofitException extends AndroidException {

    @SuppressWarnings("unused")
    RetrofitException(final RetrofitError error) {
        super(error);
    }

    @NonNull
    @Override
    public String toString() {
        final RetrofitError error = (RetrofitError) getCause();
        if (error == null) return "null";

        try {
            return String.format("RetrofitError (%s, %s, %s)", error.getKind().name(), error.getUrl(), error.toString());
        }
        catch (Exception e) {
            return "can not handle RetrofitError";
        }
    }
}
