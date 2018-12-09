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

import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils.TypeHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.ConverterHelper;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;
import akha.yakhont.technology.rx.Rx.RxSubscription;
import akha.yakhont.technology.rx.Rx2.Rx2Disposable;

import android.app.Activity;
import android.arch.lifecycle.ViewModelStore;
import android.content.ContentValues;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Retrofit2LoaderWrapper<D, T> extends BaseResponseLoaderWrapper<Callback<D>, Response<D>, Throwable, D> {

    private static final   Annotation[]                     EMPTY_ANNOTATIONS   = new Annotation[] {};

    private final          Retrofit2<T, D>                  mRetrofit;

    public Retrofit2LoaderWrapper(@NonNull final ViewModelStore         viewModelStore,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String                 table, final String description,
                                  @NonNull final Retrofit2<T, D>        retrofit) {
        this(viewModelStore, null, requester, table, description,
                BaseResponseLoaderWrapper.<D>getDefaultConverter(), getDefaultUriResolver(), retrofit);
    }

    public Retrofit2LoaderWrapper(@NonNull final ViewModelStore viewModelStore, final String loaderId,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String                 table,  final String description,
                                  @NonNull final Converter<D>           converter,
                                  @NonNull final UriResolver            uriResolver,
                                  @NonNull final Retrofit2<T, D>        retrofit) {
        super(viewModelStore, loaderId, requester, table, description, converter, uriResolver);

        mRetrofit = retrofit;

        //noinspection Convert2Lambda
        mConverter.setConverterGetter(new BaseResponse.ConverterGetter<D>() {
            @Override
            public ConverterHelper<D> get(Type type) {
                if (type == null) type = getType();
                return type == null ? null: new ConverterHelperRetrofit2<>(getConverter(type));
            }
        });
    }

    @Override
    protected BaseResponse<Response<D>, Throwable, D> makeRequest(
            @NonNull final BaseViewModel<BaseResponse<Response<D>, Throwable, D>> baseViewModel) {
        mRequester.makeRequest(new Callback<D>() {
            @Override
            public void onResponse(Call<D> call, Response<D> response) {
                mRetrofit.clearCall();
                onSuccess(call, response, baseViewModel);
            }

            @Override
            public void onFailure(Call<D> call, Throwable throwable) {
                mRetrofit.clearCall();
                onError(call, null, throwable, baseViewModel);
            }
        });
/*
            // java.lang.ClassCastException: Couldn't convert result of type
            // io.reactivex.internal.observers.LambdaObserver to io.reactivex.Observable
            catch (ClassCastException exception) {
                Utils.check(exception, "io.reactivex.internal.");
                CoreLogger.log(Level.WARNING, "it seems an API bug", exception);
            }
*/
        return null;
    }

    @Override
    protected boolean cancelRequest(@NonNull final Level level) {
        final Runnable cancelHandler = mRetrofit.getCancelHandler();
        if (cancelHandler == null)
            CoreLogger.log(level, "request cancelling handler is not defined");
        else
            try {
                cancelHandler.run();
                return true;
            }
            catch (Exception exception) {
                CoreLogger.log("request cancelling handler failed", exception);
            }
        return false;
    }

    private void onError(@SuppressWarnings("UnusedParameters") final Call<D> call,
                         final Response<D> response, final Throwable error,
                         @NonNull final BaseViewModel<BaseResponse<Response<D>, Throwable, D>> baseViewModel) {
        //noinspection Convert2Diamond
        baseViewModel.getData().onComplete(false, new BaseResponse<>(
                null, response, null, error, Source.NETWORK, null));
    }

    private void onSuccess(final Call<D> call, final Response<D> response,
                           @NonNull final BaseViewModel<BaseResponse<Response<D>, Throwable, D>> baseViewModel) {
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

            baseViewModel.getData().onComplete(true, baseResponse);
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
        onError(call, response, new Exception("error code " + code), baseViewModel);
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
                CoreLogger.log(exception);
                return null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Retrofit2LoaderBuilder<D, T> extends BaseResponseLoaderExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        private final Retrofit2<T, D>                       mRetrofit;
        private final LoaderRx<Response<D>, Throwable, D>   mRx;

        public Retrofit2LoaderBuilder(@NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {

            mRetrofit = retrofit;
            mRx       = rx;
        }

        public Retrofit2LoaderBuilder(final Fragment                            fragment,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(fragment);

            mRetrofit = retrofit;
            mRx       = rx;
        }

        public Retrofit2LoaderBuilder(final Activity                            activity,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(activity);

            mRetrofit = retrofit;
            mRx       = rx;
        }

        public Retrofit2LoaderBuilder(final ViewModelStore                      viewModelStore,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(viewModelStore);

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
        protected Retrofit2LoaderWrapper<D, T> createBaseResponseLoaderWrapper(
                final ViewModelStore viewModelStore) {
            final Method method = mRetrofit.getMethod(getRequester());

            final Retrofit2LoaderWrapper<D, T> result = new Retrofit2LoaderWrapper<>(viewModelStore,
                    mLoaderId, getRequester(), getTableName(method), mDescription, getConverter(),
                    getUriResolver(), mRetrofit);

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
                if (params.length == 0 && TypeHelper.checkType(type, TypeHelper.getType(method)))
                    return method;
            }
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Retrofit2CoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        private final Retrofit2<T, D>                        mRetrofit;

        @SuppressWarnings("unused")
        public Retrofit2CoreLoadBuilder(@NonNull final Retrofit2<T, D> retrofit) {
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
            final Retrofit2LoaderBuilder<D, T> builder = new Retrofit2LoaderBuilder<>(mRetrofit, mRx);

            if (mType != null) builder.setType(mType);

            return create(builder);
        }
    }
}
