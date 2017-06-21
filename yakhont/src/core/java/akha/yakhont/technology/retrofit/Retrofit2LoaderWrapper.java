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
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.BaseLoader.CoreLoadExtendedBuilder;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.BaseResponseLoaderExtendedWrapper;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2AdapterWrapper;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Extends the {@link BaseResponseLoaderExtendedWrapper} class to provide Retrofit 2 support.
 *
 * @param <D>
 *        The type of data
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public class Retrofit2LoaderWrapper<D> extends BaseResponseLoaderExtendedWrapper<Callback<D>, Response<D>, Throwable, D> {

    private           Method                                                            mMethod;

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
     * @param tableName
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     */
    @SuppressWarnings("unused")
    public Retrofit2LoaderWrapper(@NonNull final Context context,
                                  @NonNull final Fragment fragment,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String tableName, final String description) {
        this(context, fragment, null, requester, Core.TIMEOUT_CONNECTION, tableName, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver());
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
    public Retrofit2LoaderWrapper(@NonNull final Context context,
                                  @NonNull final Fragment fragment, final Integer loaderId,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @IntRange(from = 1) final int timeout,
                                  @NonNull final String tableName, final String description,
                                  @NonNull final Converter<D> converter,
                                  @NonNull final UriResolver uriResolver) {
        super(context, fragment, loaderId, requester, tableName, description, converter, uriResolver);

        @SuppressWarnings("unchecked")
        final BaseLoader<Callback<D>, Response<D>, Throwable, D>[] baseLoader =
                (BaseLoader<Callback<D>, Response<D>, Throwable, D>[]) new BaseLoader[1];

        setLoaderParameters(baseLoader, timeout, new Callback<D>() {
                @Override
                public void onResponse(Call<D> call, Response<D> response) {
                    onSuccess(call, response, baseLoader[0]);
                }

                @Override
                public void onFailure(Call<D> call, Throwable throwable) {
                    onError(call, null, throwable, baseLoader[0]);
                }
            });
    }

    private void onSuccess(final Call<D> call, final Response<D> response,
                           final BaseLoader<Callback<D>, Response<D>, Throwable, D> loader) {
        if (response.isSuccessful()) {
            loader.callbackHelper(true, new BaseResponse<Response<D>, Throwable, D>(
                    response.body(), response, null, null, Source.NETWORK, null));
            return;
        }

        final ResponseBody errorBody = response.errorBody();
        CoreLogger.logError("error " + errorBody);
        
        final int code = response.code();
        onError(call, response, new Exception("error code " + code), loader);
    }

    private void onError(@SuppressWarnings("UnusedParameters") final Call<D> call,
                         final Response<D> response, final Throwable error,
                         final BaseLoader<Callback<D>, Response<D>, Throwable, D> loader) {
        loader.callbackHelper(false, new BaseResponse<Response<D>, Throwable, D>(
                null, response, null, error, Source.NETWORK, null));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected Type getTypeHelper() {
        return Retrofit2LoaderBuilder.getType(mMethod);
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

        private final Retrofit2<T>                                                      mRetrofit;

        /**
         * Initialises a newly created {@code Retrofit2LoaderBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data
         *
         * @param retrofit
         *        The Retrofit2 component
         */
        @SuppressWarnings("unused")
        public Retrofit2LoaderBuilder(@NonNull final Fragment fragment, @NonNull final Class<D> type,
                                      @NonNull final Retrofit2<T> retrofit) {
            super(fragment, type);
            mRetrofit = retrofit;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        public Requester<Callback<D>> getDefaultRequester() {
            return getRequester(new RequesterHelper<Callback<D>, D, T>(mType) {
                @Override
                public void init() {
                    mMethod  = findMethod(mRetrofit.getService(), mClass);
                    mHandler = mRetrofit.getApi();
                }

                @Override
                public void request(Callback<D> callback) throws Exception {
                    @SuppressWarnings("unchecked")
                    final Call<D> call = (Call<D>) mMethod.invoke(mHandler);
                    call.enqueue(callback);
                }
            });
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        protected Type getTypeHelper() {
            return getType(findMethod());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        @Override
        protected Retrofit2LoaderWrapper<D> createLoaderWrapper() {
            final Retrofit2LoaderWrapper<D> loaderWrapper = new Retrofit2LoaderWrapper<>(getContext(),
                    getFragment(), mLoaderId, getRequester(), getTimeout(), getTableName(),
                    mDescription, getConverter(), getUriResolver());
            loaderWrapper.mMethod = findMethod();
            return loaderWrapper;
        }

        @SuppressWarnings("unchecked")
        private Method findMethod() {
            return findMethod(mRetrofit.getService(), (Class<D>) mType);
        }

        private static <D, T> Method findMethod(@NonNull final Class<T> service, @NonNull final Class<D> responseType) {
            for (final Method method: service.getMethods())
                if (Utils.checkType(responseType, getType(method)))
                    return method;
            return null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Type getType(final Method method) {
            if (method == null) return null;

            final Type type = method.getGenericReturnType();
            if (type instanceof ParameterizedType)
                return ((ParameterizedType) type).getActualTypeArguments()[0];

            return type;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@yakhont.link CoreLoad} objects. Creates the Retrofit2-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit 2 API
     */
    public static class Retrofit2CoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        private final Retrofit2<T>      mRetrofit;

        /**
         * Initialises a newly created {@code Retrofit2CoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data
         *
         * @param retrofit
         *        The Retrofit2 component
         */
        @SuppressWarnings("unused")
        public Retrofit2CoreLoadBuilder(@NonNull final Fragment fragment, @NonNull final Class<D> type,
                                        @NonNull final Retrofit2<T> retrofit) {
            super(fragment, type);
            mRetrofit = retrofit;
        }
/*
        / @exclude / @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
        public Retrofit2CoreLoadBuilder(
                @NonNull final CoreLoadExtendedBuilder<Callback<D>, Response<D>, Throwable, D> src,
                @NonNull final Retrofit2<T> retrofit) {
            super(src);
            mRetrofit = retrofit;
        }
*/
        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        protected void customizeAdapterWrapper(@NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
            setAdapterWrapper(mFrom == null ? new Retrofit2AdapterWrapper<D>(mFragment.getActivity(), item):
                    new Retrofit2AdapterWrapper<D>(mFragment.getActivity(), item, mFrom, mTo));
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public T getApi() {
            return mRetrofit.getApi();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public CoreLoad create() {
            return create(new Retrofit2LoaderBuilder<>(mFragment, mType, mRetrofit));
        }
    }
}
