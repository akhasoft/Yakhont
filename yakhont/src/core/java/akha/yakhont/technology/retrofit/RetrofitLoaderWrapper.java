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
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.BaseLoader.CoreLoadExtendedBuilder;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.BaseResponseLoaderExtendedWrapper;
import akha.yakhont.technology.retrofit.Retrofit;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitAdapterWrapper;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.util.AndroidException;
import android.view.View;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.YakhontRestAdapter.YakhontCallback;
import retrofit.client.Response;

/**
 * Extends the {@link BaseResponseLoaderExtendedWrapper} class to provide Retrofit support.
 *
 * @param <D>
 *        The type of data
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public class RetrofitLoaderWrapper<D> extends BaseResponseLoaderExtendedWrapper<Callback<D>, Response, Exception, D> {

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
     * @param tableName
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     */
    @SuppressWarnings("unused")
    public RetrofitLoaderWrapper(@NonNull final Context context,
                                 @NonNull final Fragment fragment,
                                 @NonNull final Requester<Callback<D>> requester,
                                 @NonNull final String tableName, final String description) {
        this(context, fragment, null, requester, Core.TIMEOUT_CONNECTION, tableName, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver());
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
     * @param tableName
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
     */
    @SuppressWarnings("WeakerAccess")
    public RetrofitLoaderWrapper(@NonNull final Context context,
                                 @NonNull final Fragment fragment, final Integer loaderId,
                                 @NonNull final Requester<Callback<D>> requester,
                                 @IntRange(from = 1) final int timeout,
                                 @NonNull final String tableName, final String description,
                                 @NonNull final Converter<D> converter,
                                 @NonNull final UriResolver uriResolver) {
        super(context, fragment, loaderId, requester, tableName, description, converter, uriResolver);

        @SuppressWarnings("unchecked")
        final BaseLoader<Callback<D>, Response, Exception, D>[] baseLoader =
                (BaseLoader<Callback<D>, Response, Exception, D>[]) new BaseLoader[1];

        setLoaderParameters(baseLoader, timeout, mType != null ? new Callback<D>() {
                    @Override
                    public void success(final D result, final Response response) {
                        onSuccess(result, response, baseLoader[0]);
                    }

                    @Override
                    public void failure(final RetrofitError error) {
                        onError(error, baseLoader[0]);
                    }
                }: new YakhontCallback<D>() {
                    @Override
                    public boolean setType(final Type type) {
                        synchronized (mTypeLock) {
                            onSetType(type);
                        }
                        return false;
                    }

                    @Override
                    public void success(final D result, final Response response) {
                        onSuccess(result, response, baseLoader[0]);
                    }

                    @Override
                    public void failure(final RetrofitError error) {
                        onError(error, baseLoader[0]);
                    }
                });
    }

    private void onSuccess(final D result, final Response response, final BaseLoader<Callback<D>, Response, Exception, D> loader) {
        loader.callbackHelper(true, new BaseResponse<Response, Exception, D>(
                result, response, null, null, Source.NETWORK, null));
    }

    private void onError(final RetrofitError error, final BaseLoader<Callback<D>, Response, Exception, D> loader) {
        loader.callbackHelper(false, new BaseResponse<Response, Exception, D>(
                null, null, null, new RetrofitException(error), Source.NETWORK, error));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected Type getTypeHelper() {
        return getType(mRequester);
    }

    private static <D> Type getType(@NonNull final Requester<Callback<D>> requester) {
        final Type[] typeHelper = new Type[1];
        requester.makeRequest(new YakhontCallback<D>() {
            @Override
            public boolean setType(final Type type) {
                typeHelper[0] = type;
                return true;
            }
            @Override public void success(final D result, final Response response) {}
            @Override public void failure(final RetrofitError error)               {}
        });

        // in this special case it's not needed to wait for YakhontCallback.setType() to complete -
        // everything goes in the same thread
        return typeHelper[0];
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

        private final Retrofit<T>                                                       mRetrofit;

        /**
         * Initialises a newly created {@code RetrofitLoaderBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitLoaderBuilder(@NonNull final Fragment fragment, @NonNull final Type type,
                                     @NonNull final Retrofit<T> retrofit) {
            super(fragment, type);
            mRetrofit = retrofit;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        public Requester<Callback<D>> getDefaultRequester() {
            return getRequester(new RequesterHelper<Callback<D>, T>(mType) {
                @Override
                public void init() {
                    mMethod  = mRetrofit.getYakhontRestAdapter().findMethod(mType);
                    mHandler = mRetrofit.getYakhontRestAdapter().getHandler();
                }

                @Override
                public void request(@NonNull final Callback<D> callback) throws Exception {
                    CoreReflection.invoke(mHandler, mMethod, callback);
                }
            });
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        protected Type getTypeHelper() {
            return RetrofitLoaderWrapper.getType(mRequester);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        @Override
        protected RetrofitLoaderWrapper<D> createLoaderWrapper() {
            return new RetrofitLoaderWrapper<>(getContext(), getFragment(), mLoaderId, getRequester(),
                    getTimeout(), getTableName(), mDescription, getConverter(), getUriResolver());
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

        private final Retrofit<T>       mRetrofit;

        /**
         * Initialises a newly created {@code RetrofitCoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitCoreLoadBuilder(@NonNull final Fragment fragment, @NonNull final Class<D> type,
                                       @NonNull final Retrofit<T> retrofit) {
            this(fragment, (Type) type, retrofit);
        }

        /**
         * Initialises a newly created {@code RetrofitCoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data; for generic {@link java.util.Collection} types please use {@link TypeToken}
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitCoreLoadBuilder(@NonNull final Fragment fragment, @NonNull final Type type,
                                       @NonNull final Retrofit<T> retrofit) {
            super(fragment, type);
            mRetrofit = retrofit;
        }

        /**
         * Initialises a newly created {@code RetrofitCoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data; intended to use with generic {@link java.util.Collection} types,
         *        e.g. {@code new com.google.gson.reflect.TypeToken<List<MyData>>() {}}
         *
         * @param retrofit
         *        The Retrofit component
         */
        @SuppressWarnings("unused")
        public RetrofitCoreLoadBuilder(@NonNull final Fragment fragment, @NonNull final TypeToken type,
                                       @NonNull final Retrofit<T> retrofit) {
            super(fragment, type);
            mRetrofit = retrofit;
        }
/*
        / @exclude / @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
        public RetrofitCoreLoadBuilder(
                @NonNull final CoreLoadExtendedBuilder<Callback<D>, Response, Exception, D> src,
                @NonNull final Retrofit<T> retrofit) {
            super(src);
            mRetrofit = retrofit;
        }
*/
        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        protected void customizeAdapterWrapper(@NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
            setAdapterWrapper(mFrom == null ? new RetrofitAdapterWrapper<D>(mFragment.get().getActivity(), item):
                    new RetrofitAdapterWrapper<D>(mFragment.get().getActivity(), item, mFrom, mTo));
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public T getApi() {
            return mRetrofit.getYakhontRestAdapter().getHandler();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public CoreLoad create() {
            return create(new RetrofitLoaderBuilder<D, T>(mFragment.get(), mType, mRetrofit));
        }
    }
}

class RetrofitException extends AndroidException {

    @SuppressWarnings("unused")
    RetrofitException(final RetrofitError error) {
        super(error);
    }

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
