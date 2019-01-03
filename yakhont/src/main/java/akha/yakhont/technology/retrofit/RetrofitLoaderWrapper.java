/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
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

import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils.TypeHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.ConverterHelper;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;

import android.app.Activity;
import android.content.ContentValues;
import android.util.AndroidException;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelStore;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedString;

public class RetrofitLoaderWrapper<D, T> extends BaseResponseLoaderWrapper<Callback<D>, Response, Exception, D> {

    private final          Retrofit<T, D>                    mRetrofit;

    public RetrofitLoaderWrapper(@NonNull final ViewModelStore          viewModelStore,
                                 @NonNull final Requester<Callback<D>>  requester,
                                 @NonNull final String                  table, final String description,
                                 @NonNull final Retrofit<T, D>          retrofit) {
        //noinspection RedundantTypeArguments
        this(viewModelStore, null, requester, table, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver(), retrofit);
    }

    public RetrofitLoaderWrapper(@NonNull final ViewModelStore viewModelStore, final String loaderId,
                                 @NonNull final Requester<Callback<D>> requester,
                                 @NonNull final String         table, final String description,
                                 @NonNull final Converter<D>   converter,
                                 @NonNull final UriResolver    uriResolver,
                                 @NonNull final Retrofit<T, D> retrofit) {
        super(viewModelStore, loaderId, requester, table, description, converter, uriResolver);

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
    }

    @Override
    protected BaseResponse<Response, Exception, D> makeRequest(
            @NonNull final BaseViewModel<BaseResponse<Response, Exception, D>> baseViewModel) {
        mRequester.makeRequest(new Callback<D>() {
            @Override
            public void success(final D result, final Response response) {
                mRetrofit.clearCall();
                onSuccess(result, response, baseViewModel);
            }

            @Override
            public void failure(final RetrofitError error) {
                mRetrofit.clearCall();
                onError(error, baseViewModel);
            }
        });
        return null;
    };

    private void onError(final RetrofitError error,
                         @NonNull final BaseViewModel<BaseResponse<Response, Exception, D>> baseViewModel) {
        //noinspection Convert2Diamond
        baseViewModel.getData().onComplete(false, new BaseResponse<Response, Exception, D>(
                null, null, null, new RetrofitException(error), Source.NETWORK, error));
    }

    private void onSuccess(final D result, final Response response,
                           @NonNull final BaseViewModel<BaseResponse<Response, Exception, D>> baseViewModel) {
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
        baseViewModel.getData().onComplete(true, baseResponse);
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
                CoreLogger.log(exception);
                return null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class RetrofitLoaderBuilder<D, T> extends BaseResponseLoaderExtendedBuilder<Callback<D>, Response, Exception, D, T> {

        private final Retrofit<T, D>                                 mRetrofit;

        public RetrofitLoaderBuilder(@NonNull final Retrofit<T, D> retrofit) {

            mRetrofit = retrofit;
        }

        public RetrofitLoaderBuilder(final Fragment                fragment,
                                     @NonNull final Retrofit<T, D> retrofit) {
            super(fragment);

            mRetrofit = retrofit;
        }

        public RetrofitLoaderBuilder(final Activity activity,
                                     @NonNull final Retrofit<T, D> retrofit) {
            super(activity);

            mRetrofit = retrofit;
        }

        public RetrofitLoaderBuilder(final ViewModelStore          viewModelStore,
                                     @NonNull final Retrofit<T, D> retrofit) {
            super(viewModelStore);

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
        protected RetrofitLoaderWrapper<D, T> createBaseResponseLoaderWrapper(
                final ViewModelStore viewModelStore) {
            final Method method = mRetrofit.getMethod(getRequester());

            final RetrofitLoaderWrapper<D, T> result = new RetrofitLoaderWrapper<>(viewModelStore,
                    mLoaderId, getRequester(), getTableName(method), mDescription, getConverter(),
                    getUriResolver(), mRetrofit);

            setType(result, mRetrofit.getYakhontRestAdapter().getType(method));
            return result;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class RetrofitCoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response, Exception, D, T> {

        private final Retrofit<T, D>                                 mRetrofit;

        @SuppressWarnings("unused")
        public RetrofitCoreLoadBuilder(@NonNull final Retrofit<T, D> retrofit) {
            super();

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
            final RetrofitLoaderBuilder<D, T> builder = new RetrofitLoaderBuilder<>(mRetrofit);

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
