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

import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.CoreLoadHelper;
import akha.yakhont.Core.Utils.TypeHelper;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseRecyclerViewAdapter;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.OnItemClickListener;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefault;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters;            // for javadoc
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeToRefreshWrapper;     // for javadoc
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;          // for javadoc
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoader;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallbacks;   // for javadoc
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
import akha.yakhont.technology.rx.BaseRx;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;
import akha.yakhont.technology.rx.Rx3.Rx3Disposable;

import android.app.Activity;
import android.content.ContentValues;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;                                    // for javadoc
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.paging.DataSource;
import androidx.paging.PagedList.Config;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import okhttp3.OkHttpClient;
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
public class Retrofit2LoaderWrapper<D, T> extends BaseResponseLoaderWrapper<Callback<D>, Response<D>, Throwable, D> {

    private static final   Annotation[]                     EMPTY_ANNOTATIONS   = new Annotation[] {};

    private        final   Retrofit2<T, D>                  mRetrofit;

    /**
     * Initialises a newly created {@code Retrofit2LoaderWrapper} object.
     *
     * @param viewModelStore
     *        The {@code ViewModelStore}
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
    public Retrofit2LoaderWrapper(@NonNull final ViewModelStore         viewModelStore,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String                 table,
                                           final String                 description,
                                  @NonNull final Retrofit2<T, D>        retrofit) {
        this(viewModelStore, null, requester, table, description,
                BaseResponseLoaderWrapper.getDefaultConverter(), getDefaultUriResolver(), retrofit);
    }

    /**
     * Initialises a newly created {@code Retrofit2LoaderWrapper} object.
     *
     * @param viewModelStore
     *        The {@code ViewModelStore}
     *
     * @param loaderId
     *        The loader ID
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
     * @param converter
     *        The data converter
     *
     * @param uriResolver
     *        The URI resolver (for cache provider)
     *
     * @param retrofit
     *        The Retrofit2 component
     */
    @SuppressWarnings("WeakerAccess")
    public Retrofit2LoaderWrapper(@NonNull final ViewModelStore         viewModelStore,
                                           final String                 loaderId,
                                  @NonNull final Requester<Callback<D>> requester,
                                  @NonNull final String                 table,
                                           final String                 description,
                                  @NonNull final Converter<D>           converter,
                                  @NonNull final UriResolver            uriResolver,
                                  @NonNull final Retrofit2<T, D>        retrofit) {
        super(viewModelStore, loaderId, requester, table, description, converter, uriResolver);

        mRetrofit = retrofit;

        //noinspection Convert2Lambda
        mConverter.setConverterGetter(new ConverterGetter<D>() {
            @SuppressWarnings("unused")
            @Override
            public ConverterHelper<D> get(Type type) {
                if (type == null) type = getType();
                return type == null ? null: new ConverterHelperRetrofit2<>(getConverter(type));
            }
        });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected BaseResponse<Response<D>, Throwable, D> makeRequest(
            @NonNull final BaseViewModel<BaseResponse<Response<D>, Throwable, D>> baseViewModel) {
        try {
            mRequester.makeRequest(new Callback<D>() {
                @Override
                public void onResponse(@NonNull final Call<D> call, @NonNull final Response<D> response) {
                    mRetrofit.clearCall();
                    onSuccess(call, response, baseViewModel);
                }

                @Override
                public void onFailure(@NonNull final Call<D> call, @NonNull final Throwable throwable) {
                    mRetrofit.clearCall();
                    onError(call, null, throwable, baseViewModel);
                }
            });
        }
        // java.lang.ClassCastException: Couldn't convert result of type
        // io.reactivex.internal.observers.LambdaObserver to io.reactivex.Observable
        // or
        // java.lang.ClassCastException: Couldn't convert result of type
        // io.reactivex.rxjava3.internal.observers.LambdaObserver to io.reactivex.rxjava3.core.Observable
        catch (ClassCastException exception) {
            // ugly hack for nasty bug :-)
            final String message = exception.getMessage();
            if (message == null || !message.contains(".internal.observers.LambdaObserver to io.reactivex."))
                throw exception;
            CoreLogger.log(Level.WARNING, "it seems a Rx API bug", exception);
        }
        return null;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected boolean cancelRequest(@NonNull final Level level) {
        final Runnable cancelHandler = mRetrofit.getCancelHandler();
        if (cancelHandler == null)
            CoreLogger.log(level, "request cancelling handler is not defined, object " + this);
        else
            try {
                CoreLogger.log(level, "about to cancel Retrofit data loading, object " + this);

                Utils.safeRun(cancelHandler);
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
        baseViewModel.getData().onComplete(false, new BaseResponse<>(
                mParameters, null, response, null, error, Source.NETWORK, null));
    }

    private void onSuccess(final Call<D> call, final Response<D> response,
                           @NonNull final BaseViewModel<BaseResponse<Response<D>, Throwable, D>> baseViewModel) {
        if (response.isSuccessful()) {
            final D body = response.body();
            final BaseResponse<Response<D>, Throwable, D> baseResponse = new BaseResponse<>(
                    mParameters, body, response, null, null, Source.NETWORK, null);

            if (body != null) {
                final Class<?> type = body.getClass();      // collections are without generic info
                setTypeIfNotSet(TypeHelper.getType(type));

                final Collection<ContentValues> contentValues = getDataForCache(type);
                if (contentValues != null)
                    baseResponse.setValues(contentValues);
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

    private Collection<ContentValues> getDataForCache(@NonNull final Class<?> type) {
        final BodyCache data = mRetrofit.getData();
        if (data == null)
            CoreLogger.logError("no data to cache found; if you're using your own " +
                    "OkHttpClient, please consider to add BodySaverInterceptor "        +
                    "(for working example please refer to LocalOkHttpClient2 in demo-simple)");
        return data == null ? null: mConverter.getValues(data.getType(), data.getResponse(),
                type, mParameters.getPageId());
    }

    private retrofit2.Converter<ResponseBody, D> getConverter(final Type type) {
        if (type == null) {
            CoreLogger.logError("type == null");
            return null;
        }
        try {
            final retrofit2.Converter<ResponseBody, D> converter =
                    mRetrofit.getRetrofit().responseBodyConverter(type, EMPTY_ANNOTATIONS);
            //noinspection ConstantConditions
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

        @SuppressWarnings("unused")
        private ConverterHelperRetrofit2(final retrofit2.Converter<ResponseBody, D> converter) {
            mConverter = converter;
        }

        @SuppressWarnings("unused")
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
         *        The Fragment
         *
         * @param retrofit
         *        The Retrofit2 component
         *
         * @param rx
         *        The Rx component
         */
        @SuppressWarnings("unused")
        public Retrofit2LoaderBuilder(final Fragment                            fragment,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(fragment);

            mRetrofit = retrofit;
            mRx       = rx;
        }

        /**
         * Initialises a newly created {@code Retrofit2LoaderBuilder} object.
         *
         * @param activity
         *        The Activity
         *
         * @param retrofit
         *        The Retrofit2 component
         *
         * @param rx
         *        The Rx component
         */
        @SuppressWarnings("unused")
        public Retrofit2LoaderBuilder(final Activity                            activity,
                                      @NonNull final Retrofit2<T, D>            retrofit,
                                      final LoaderRx<Response<D>, Throwable, D> rx) {
            super(activity);

            mRetrofit = retrofit;
            mRx       = rx;
        }

        /**
         * Initialises a newly created {@code Retrofit2LoaderBuilder} object.
         *
         * @param viewModelStore
         *        The ViewModelStore
         *
         * @param retrofit
         *        The Retrofit2 component
         *
         * @param rx
         *        The Rx component
         */
        @SuppressWarnings("unused")
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
                public void request(final Callback<D> callback) throws Throwable {
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
                    mLoaderId, getRequester(), mTableName != null ? mTableName: getTableName(method),
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
                if (params.length == 0 && TypeHelper.checkType(type, TypeHelper.getType(method)))
                    return method;
            }
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link CoreLoad} objects. Creates the Retrofit2-based ones.
     *
     * @param <D>
     *        The type of data
     *
     * @param <T>
     *        The type of Retrofit 2 API
     */
    public static class Retrofit2CoreLoadBuilder<D, T> extends CoreLoadExtendedBuilder<Callback<D>, Response<D>, Throwable, D, T> {

        private final Retrofit2<T, D>                        mRetrofit;
        private final ViewModelStore                         mViewModelStore;

        /**
         * Initialises a newly created {@code Retrofit2CoreLoadBuilder} object.
         *
         * @param retrofit
         *        The Retrofit2 component
         */
        @SuppressWarnings("unused")
        public Retrofit2CoreLoadBuilder(@NonNull final Retrofit2<T, D> retrofit) {
            this(retrofit, null);
        }

        /**
         * Initialises a newly created {@code Retrofit2CoreLoadBuilder} object.
         *
         * @param retrofit
         *        The Retrofit2 component
         *
         * @param viewModelStore
         *        The ViewModelStore component
         */
        @SuppressWarnings("WeakerAccess")
        public Retrofit2CoreLoadBuilder(@NonNull final Retrofit2<T, D> retrofit,
                                        final ViewModelStore  viewModelStore) {
            super();

            mRetrofit           = retrofit;
            mViewModelStore     = viewModelStore;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public T getApi(final Callback<D> callback) {
            return mRetrofit.getApi(callback);
        }

        /**
         * Returns the {@link Rx3Disposable} component.
         *
         * @return  The {@link Rx3Disposable}
         */
        @SuppressWarnings("unused")
        public Rx3Disposable getRxDisposableHandler() {
            return Retrofit2.getRxDisposableHandler(mRx);
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
        public CoreLoad<Throwable, D> create() {
            final Retrofit2LoaderBuilder<D, T> builder = new Retrofit2LoaderBuilder<>(
                    mViewModelStore, mRetrofit, mRx);

            if (mType != null) builder.setType(mType);

            return create(builder);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Helper class for Retrofit 2. Creates data loader, handles screen orientation changing and
     * enables swipe-to-refresh (if available). Very simplified usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * public class YourActivity extends AppCompatActivity {
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *
     *         super.onCreate(savedInstanceState);

     *         // your code here: setContentView(...), RecyclerView.setLayoutManager(...), etc.
     *
     *         Retrofit2Loader.start("https://...", Retrofit2Api.class, Retrofit2Api::getData, BR.id, savedInstanceState);
     *     }
     * }
     * </pre>
     */
    @SuppressWarnings({"JavadocReference", "WeakerAccess", "unused"})
    public static class Retrofit2Loader<E, D> extends CoreLoader<E, D> {

        /** Stub to use in services. */
        public static final  Bundle                         SERVICE_STUB        = new Bundle();

        /**
         * Starts data loading.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param dataBinding
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @see CoreLoad#start(Bundle)
         */
        public static <R> void start(@NonNull final String                  url,
                                     @NonNull final Class    <R>            service,
                                     @NonNull final Requester<R>            requester,
                                              final Integer                 dataBinding,
                                              final Bundle                  savedInstanceState) {
            start(get(url, service, requester, null, dataBinding, null,
                    null, null, null, null,
                    null, null, null,
                    BaseViewModel.cast(Utils.getCurrentActivity(), null), null,
                    null, savedInstanceState), savedInstanceState);
        }

        /**
         * Starts data loading.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param dataBinding
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         *
         * @param onItemClickListener
         *        The {@code OnItemClickListener} (or null for no item's click handling)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @see CoreLoad#start(Bundle)
         */
        public static <R> void start(@NonNull final String                  url,
                                     @NonNull final Class    <R>            service,
                                     @NonNull final Requester<R>            requester,
                                              final String                  viewModelKey,
                                              final ViewModelStoreOwner     viewModelStoreOwner,
                                              final Integer                 dataBinding,
                                              final OnItemClickListener     onItemClickListener,
                                              final Bundle                  savedInstanceState) {
            start(get(url, service, requester, viewModelKey, dataBinding, null, null,
                    null, null, null, null,
                    null, null, viewModelStoreOwner, null,
                    onItemClickListener, savedInstanceState), savedInstanceState);
        }

        private static <D> void start(final CoreLoad<Throwable, D> coreLoad, final Bundle savedInstanceState) {
            if (coreLoad != null && savedInstanceState == null) coreLoad.start(null);
        }

        /**
         * Starts data loading.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param dataBinding
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         *
         * @param dataSourceProducer
         *        The {@code DataSource} producer component (for paging)
         *
         * @param onItemClickListener
         *        The {@code OnItemClickListener} (or null for no item's click handling)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @see CoreLoad#start(Bundle)
         */
        public static <R> void start(@NonNull final String                               url,
                                     @NonNull final Class    <R>                         service,
                                     @NonNull final Requester<R>                         requester,
                                              final String                               viewModelKey,
                                              final ViewModelStoreOwner                  viewModelStoreOwner,
                                              final Integer                              dataBinding,
                                              final Callable<? extends DataSource<?, ?>> dataSourceProducer,
                                              final OnItemClickListener                  onItemClickListener,
                                              final Bundle                               savedInstanceState) {
            start(get(url, service, requester, viewModelKey, dataBinding, null, null, null,
                    dataSourceProducer, null, null, null, null,
                    viewModelStoreOwner, null, onItemClickListener, savedInstanceState), savedInstanceState);
        }

        /**
         * Starts data loading.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param dataBinding
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         *
         * @param pageSize
         *        The page size (if any)
         *
         * @param dataSourceProducer
         *        The {@code DataSource} producer component (for paging)
         *
         * @param onItemClickListener
         *        The {@code OnItemClickListener} (or null for no item's click handling)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @see CoreLoad#start(Bundle)
         */
        public static <R> void start(@NonNull final String                               url,
                                     @NonNull final Class    <R>                         service,
                                     @NonNull final Requester<R>                         requester,
                                              final String                               viewModelKey,
                                              final ViewModelStoreOwner                  viewModelStoreOwner,
                                              final Integer                              dataBinding,
                                              final Integer                              pageSize,
                                              final Callable<? extends DataSource<?, ?>> dataSourceProducer,
                                              final OnItemClickListener                  onItemClickListener,
                                              final Bundle                               savedInstanceState) {
            start(get(url, service, requester, viewModelKey, dataBinding, null, null,
                    pageSize == null ? null: new Config.Builder().setPageSize(pageSize).build(),
                    dataSourceProducer, null, null,null, null,
                    viewModelStoreOwner, null, onItemClickListener, savedInstanceState), savedInstanceState);
        }

        /**
         * Starts data loading (intended to use from services).
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param rx
         *        The {@code SubscriberRx} component (or null if not used)
         *
         * @param loaderCallbacks
         *        The {@code LoaderCallbacks} component (or null if not used)
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param <D>
         *        The type of data
         *
         * @param <R>
         *        The type of Retrofit API
         */
        public static <R, D> void start(@NonNull final String                            url,
                                        @NonNull final Class    <R>                      service,
                                        @NonNull final Requester<R>                      requester,
                                                 final SubscriberRx<              D>     rx,
                                                 final LoaderCallbacks<Throwable, D>     loaderCallbacks,
                                                 final String                            viewModelKey,
                                                 final ViewModelStoreOwner               viewModelStoreOwner) {
            start(url, service, requester, rx, loaderCallbacks, viewModelKey, viewModelStoreOwner,
                    null, null);
        }

        /**
         * Starts data loading (intended to use from services).
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param rx
         *        The {@code SubscriberRx} component (or null if not used)
         *
         * @param loaderCallbacks
         *        The {@code LoaderCallbacks} component (or null if not used)
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param client
         *        The {@code OkHttpClient} component (or null for default one)
         *
         * @param retrofit
         *        The {@code Retrofit2} component (or null for default one)
         *
         * @param <D>
         *        The type of data
         *
         * @param <R>
         *        The type of Retrofit API
         */
        public static <R, D> void start(@NonNull final String                            url,
                                        @NonNull final Class    <R>                      service,
                                        @NonNull final Requester<R>                      requester,
                                                 final SubscriberRx<              D>     rx,
                                                 final LoaderCallbacks<Throwable, D>     loaderCallbacks,
                                                 final String                            viewModelKey,
                                                       ViewModelStoreOwner               viewModelStoreOwner,
                                                 final OkHttpClient                      client,
                                                 final Retrofit2<R,               D>     retrofit) {
            if (viewModelStoreOwner == null) viewModelStoreOwner = CoreLoader.INSTANCE;

            final CoreLoad<Throwable, D> coreLoad = get(url, service, requester, viewModelKey,
                    null, client, retrofit, null, null, rx,
                    false, loaderCallbacks, true, viewModelStoreOwner,
                    null, null, SERVICE_STUB);

            if (coreLoad != null)
                coreLoad.start(viewModelStoreOwner, BaseLoaderWrapper.getLoadParameters(null));
        }

        /**
         * Returns the {@code CoreLoad} instance.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param dataBinding
         *        The BR id of the variable to be set (or null if not used); please refer to
         *        {@link ViewDataBinding#setVariable} for more info
         *
         * @param client
         *        The {@code OkHttpClient} component (or null for default one)
         *
         * @param retrofit
         *        The {@code Retrofit2} component (or null for default one)
         *
         * @param rx
         *        The {@code SubscriberRx} component (or null if not used)
         *
         * @param loaderCallbacks
         *        The {@code LoaderCallbacks} component (or null if not used)
         *
         * @param onItemClickListener
         *        The {@code OnItemClickListener} (or null for no item's click handling)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <D>
         *        The type of data
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @return  The {@code CoreLoad} instance
         */
        @SuppressWarnings("WeakerAccess")
        public static <R, D> CoreLoad<Throwable, D> get(@NonNull final String                url,
                                                        @NonNull final Class    <R>          service,
                                                        @NonNull final Requester<R>          requester,
                                                                 final String                viewModelKey,
                                                                 final Integer               dataBinding,
                                                                 final OkHttpClient          client,
                                                                 final Retrofit2<R, D>       retrofit,
                                                                 final SubscriberRx<D>       rx,
                                                                 final LoaderCallbacks<Throwable, D>
                                                                                             loaderCallbacks,
                                                                 final OnItemClickListener   onItemClickListener,
                                                                 final Bundle                savedInstanceState) {
            return get(url, service, requester, viewModelKey, dataBinding, client, retrofit,
                    null, null, rx, null, loaderCallbacks,
                    null, null, null, onItemClickListener, savedInstanceState);
        }

        /**
         * Returns the {@code CoreLoad} instance.
         *
         * @param url
         *        The Retrofit 2 API endpoint URL
         *
         * @param service
         *        The Retrofit 2 service interface
         *
         * @param requester
         *        The data loading requester
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param dataBinding
         *        The BR id of the variable to be set (or null if not used); please refer to
         *        {@link ViewDataBinding#setVariable} for more info
         *
         * @param client
         *        The {@code OkHttpClient} component (or null for default one)
         *
         * @param retrofit
         *        The {@code Retrofit2} component (or null for default one)
         *
         * @param pagingConfig
         *        The {@code PagedList} configuration (if any, null for default one)
         *
         * @param dataSourceProducer
         *        The {@code DataSource} producer component (for paging, null for default one)
         *
         * @param rx
         *        The {@code SubscriberRx} component (or null if not used)
         *
         * @param loaderCallbacks
         *        The {@code LoaderCallbacks} component (or null if not used)
         *
         * @param isService
         *        {@code true} if working from service, {@code false} otherwise (or null for default value)
         *
         * @param viewModelStoreOwner
         *        The {@code ViewModelStoreOwner} (or null for default one)
         *
         * @param progress
         *        The custom {@code Progress} indicator (or null for default one)
         *
         * @param onItemClickListener
         *        The {@code OnItemClickListener} (or null for no item's click handling)
         *
         * @param savedInstanceState
         *        Please refer to {@link Activity#onCreate(Bundle)}
         *
         * @param <D>
         *        The type of data
         *
         * @param <R>
         *        The type of Retrofit API
         *
         * @return  The {@code CoreLoad} instance
         */
        @SuppressWarnings("WeakerAccess")
        public static <R, D> CoreLoad<Throwable, D> get(@NonNull final String                url,
                                                        @NonNull final Class    <R>          service,
                                                        @NonNull final Requester<R>          requester,
                                                                 final String                viewModelKey,
                                                                 final Integer               dataBinding,
                                                                 final OkHttpClient          client,
                                                                 final Retrofit2<R, D>       retrofit,
                                                                 final Config                pagingConfig,
                                                                 final Callable<? extends DataSource<?, ?>>
                                                                                             dataSourceProducer,
                                                                 final SubscriberRx<D>       rx,
                                                                 final LoaderCallbacks<Throwable, D>
                                                                                             loaderCallbacks,
                                                                       Boolean               isService,
                                                                 final ViewModelStoreOwner   viewModelStoreOwner,
                                                                 final Progress              progress,
                                                                 final OnItemClickListener   onItemClickListener,
                                                                 final Bundle                savedInstanceState) {
            return get(url, service, requester, viewModelKey, dataBinding, client, retrofit,
                    pagingConfig, dataSourceProducer, rx, null, loaderCallbacks, isService,
                    viewModelStoreOwner, progress, onItemClickListener, savedInstanceState);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <R, D> CoreLoad<Throwable, D> get(@NonNull final String                url,
                                                        @NonNull final Class    <R>          service,
                                                        @NonNull final Requester<R>          requester,
                                                                 final String                viewModelKey,
                                                                 final Integer               dataBinding,
                                                                 final OkHttpClient          client,
                                                                 final Retrofit2<R, D>       retrofit,
                                                                 final Config                pagingConfig,
                                                                 final Callable<? extends DataSource<?, ?>>
                                                                                             dataSourceProducer,
                                                                 final SubscriberRx<D>       rx,
                                                                       Boolean               isRxSingle,
                                                                 final LoaderCallbacks<Throwable, D>
                                                                                             loaderCallbacks,
                                                                       Boolean               isService,
                                                                 final ViewModelStoreOwner   viewModelStoreOwner,
                                                                 final Progress              progress,
                                                                 final OnItemClickListener   onItemClickListener,
                                                                 final Bundle                savedInstanceState) {
            if (isService == null) isService = savedInstanceState == SERVICE_STUB;

            View swipeView = null;
            if (isService) {
                if (dataBinding != null)
                    CoreLogger.logError("Data Binding in service: " + dataBinding);
                if (pagingConfig != null)
                    CoreLogger.logError("Paging in service, pagingConfig: " + pagingConfig);
                if (dataSourceProducer != null)
                    CoreLogger.logError("Paging in service, dataSourceProducer: " + dataSourceProducer);
            }
            else {
                final Activity activity = viewModelStoreOwner instanceof Activity ?
                        (Activity) viewModelStoreOwner: Utils.getCurrentActivity();

                if (savedInstanceState != null)
                    return getExistingLoader(viewModelKey, activity);

                if (activity != null)
                    swipeView = getSwipeView(activity);
                else
                    CoreLogger.logError("can't find swipeView for ViewModelStoreOwner: " +
                            CoreLogger.getDescription(viewModelStoreOwner));
            }

            final CoreLoad<Throwable, D> coreLoad = get(url, service, requester, viewModelKey,
                    dataBinding, client, retrofit != null ? retrofit: new Retrofit2<>(), pagingConfig,
                    dataSourceProducer, rx, isRxSingle, loaderCallbacks,
                    isService ? null: swipeView == null, viewModelStoreOwner, progress, onItemClickListener);

            if (!isService) handleSwipeToRefresh(coreLoad, swipeView);

            return adjust(coreLoad, false, viewModelKey, isService, viewModelStoreOwner);
        }

        /**
         * Returns the adjusted {@code CoreLoad} instance.
         *
         * @param coreLoad
         *        The {@code CoreLoad} instance to adjust
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
         *        for more info); could be null (for default value)
         *
         * @param isService
         *        {@code true} if working from service, {@code false} otherwise
         *
         * @param viewModelStoreOwner
         *        The ViewModelStoreOwner (or null for default one)
         *
         * @param <D>
         *        The type of data
         *
         * @return  The {@code CoreLoad} instance
         */
        public static <D> CoreLoad<Throwable, D> adjust(final CoreLoad<Throwable, D> coreLoad,
                                                        final String                 viewModelKey,
                                                        final boolean                isService,
                                                        final ViewModelStoreOwner    viewModelStoreOwner) {
            return adjust(coreLoad, !isService, viewModelKey, isService, viewModelStoreOwner);
        }

        /**
         * Returns the adjusted {@code CoreLoad} instance.
         *
         * @param coreLoad
         *        The {@code CoreLoad} instance to adjust
         *
         * @param <D>
         *        The type of data
         *
         * @return  The {@code CoreLoad} instance
         */
        public static <D> CoreLoad<Throwable, D> adjust(final CoreLoad<Throwable, D> coreLoad) {
            return adjust(coreLoad, null, false, null);
        }

        private static <D> CoreLoad<Throwable, D> adjust(final CoreLoad<Throwable, D> coreLoad,
                                                         final boolean                handleSwipeToRefresh,
                                                               String                 viewModelKey,
                                                         final boolean                isService,
                                                               ViewModelStoreOwner    viewModelStoreOwner) {
            if (coreLoad == null) {
                CoreLogger.logError("adjust(): CoreLoad == null");
                return null;
            }

            if (viewModelStoreOwner == null) viewModelStoreOwner = getDefaultViewModelStoreOwner(isService);
            if (viewModelStoreOwner == null) {
                CoreLogger.logError("adjust(): viewModelStoreOwner == null");
                return null;
            }

            coreLoad.start(viewModelStoreOwner, LoadParameters.NO_LOAD);

            if (isService) return coreLoad;

            if (handleSwipeToRefresh) handleSwipeToRefresh(coreLoad);

            // for handling screen orientation changes
            if (viewModelKey == null) //noinspection ConstantConditions
                viewModelKey = CoreLoadHelper.getLoader(coreLoad).getKey();

            final BaseViewModel<D> viewModel = BaseViewModel.get(viewModelKey);
            if (viewModel != null) viewModel.setCoreLoads(coreLoad);

            return coreLoad;
        }

        private static ViewModelStoreOwner getDefaultViewModelStoreOwner(final boolean isService) {
            return isService ? CoreLoader.INSTANCE: BaseViewModel.cast(Utils.getCurrentActivity(), null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <R, D> CoreLoad<Throwable, D> get(@NonNull final String                   url,
                                                        @NonNull final Class    <R>             service,
                                                        @NonNull final Requester<R>             requester,
                                                                 final String                   viewModelKey,
                                                                 final Integer                  dataBinding,
                                                                 final OkHttpClient             client,
                                                        @NonNull final Retrofit2<R, D>          retrofit,
                                                                 final Config                   pagingConfig,
                                                                 final Callable<? extends DataSource<?, ?>>
                                                                                                dataSourceProducer,
                                                                 final SubscriberRx<D>          rx,
                                                                       Boolean                  isRxSingle,
                                                                 final LoaderCallbacks<Throwable, D>
                                                                                                loaderCallbacks,
                                                                       Boolean                  noSwipeToRefresh,
                                                                       ViewModelStoreOwner      viewModelStoreOwner,
                                                                 final Progress                 progress,
                                                                 final OnItemClickListener      onItemClickListener) {
            final boolean isService = noSwipeToRefresh == null;
            if (noSwipeToRefresh == null) noSwipeToRefresh = true;

            if (viewModelStoreOwner == null) viewModelStoreOwner = getDefaultViewModelStoreOwner(isService);

            final ViewModelStore viewModelStore = viewModelStoreOwner == null ? null:
                    viewModelStoreOwner.getViewModelStore();

            final Retrofit2CoreLoadBuilder<D, R> builder = (Retrofit2CoreLoadBuilder<D, R>)
                    new Retrofit2CoreLoadBuilder<>(client == null ?
                            retrofit.init(service, url): retrofit.init(service, url, client),
                            viewModelStore).setRequester(requester);

            if (viewModelKey           != null) builder.setBaseViewModelKey(viewModelKey);

            if (rx                     != null) {
                if (isRxSingle == null)  isRxSingle = dataSourceProducer == null && noSwipeToRefresh;
                setRx(builder, getRx(rx, isRxSingle));
            }
            if (loaderCallbacks        != null) builder.setLoaderCallbacks(loaderCallbacks);

            if (progress               != null) builder.setProgress(progress);
            if (onItemClickListener    != null) builder.setOnItemClickListener(onItemClickListener);

            if (dataBinding            != null) builder.setDataBinding(dataBinding);
            if (dataSourceProducer     != null) builder.setPagingDataSourceProducer(dataSourceProducer);

            if (pagingConfig           != null) {
                if (isService) CoreLogger.logError("paging in service, pagingConfig: " + pagingConfig);

                if (dataSourceProducer != null)
                    builder.setPagingConfig(pagingConfig);
                else
                    CoreLogger.logError("paging config set without DataSource producer");
            }

            if (isService) {
                builder.setProgress(new ProgressDefault() {
                    @Override
                    public void show(final Activity activity) {
                        CoreLogger.logWarning("ProgressDefault.show() ignored 'cause of Service");
                    }

                    @Override
                    public boolean confirm(final Activity activity, final View view) {
                        CoreLogger.logWarning("ProgressDefault.confirm() ignored 'cause of Service");
                        return true;
                    }
                });
                builder.setNoBinding(true);
            }

            return builder.create();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static <R, D> void setRx(Retrofit2CoreLoadBuilder<D, R> builder, Retrofit2Rx retrofit2Rx) {
            builder.setRx(retrofit2Rx);
        }

        @SuppressWarnings("rawtypes")
        private static <D> Retrofit2Rx getRx(final SubscriberRx<D> rx, boolean isSingle) {
            return rx == null ? null: new Retrofit2Rx<D>(BaseRx.RxVersions.VERSION_3, isSingle).subscribeSimple(rx);
        }

        /**
         * Enables "swipe-to-refresh" feature (if exists) for the current activity.
         *
         * @param coreLoad
         *        The {@code CoreLoad} component
         *
         * @param <E>
         *        The type of error (if any)
         *
         * @param <D>
         *        The type of data to load
         *
         * @see SwipeToRefreshWrapper
         */
        public static <E, D> void handleSwipeToRefresh(final CoreLoad<E, D> coreLoad) {
            handleSwipeToRefresh(coreLoad, (Activity) null);
        }

        /**
         * Enables "swipe-to-refresh" feature (if exists) for the given activity.
         *
         * @param activity
         *        The Activity (or null for the current one)
         *
         * @param coreLoad
         *        The {@code CoreLoad} component
         *
         * @param <E>
         *        The type of error (if any)
         *
         * @param <D>
         *        The type of data to load
         *
         * @see SwipeToRefreshWrapper
         */
        public static <E, D> void handleSwipeToRefresh(final CoreLoad<E, D> coreLoad, final Activity activity) {
            handleSwipeToRefresh(coreLoad, getSwipeView(activity));
        }

        private static <E, D> void handleSwipeToRefresh(final CoreLoad<E, D> coreLoad, final View swipeView) {
            if (swipeView != null) SwipeToRefreshWrapper.register((SwipeRefreshLayout) swipeView, coreLoad);
        }

        private static View getSwipeView(final Activity activity) {
            final View root = Utils.getDefaultView(activity);
            //noinspection Convert2Lambda
            return root == null ? null: ViewHelper.findView(root, new ViewHelper.ViewVisitor() {
                @SuppressWarnings("unused")
                @Override
                public boolean handle(final View view) {
                    return view instanceof SwipeRefreshLayout;
                }
            }, CoreLogger.getDefaultLevel());
        }

        /**
         * Returns the {@link CoreLoad} kept in the current {@link ViewModel} (mostly for screen
         * orientation changes handling); also handles swipe-to-refresh (if available).
         *
         * @param <E>
         *        The type of error (if any)
         *
         * @param <D>
         *        The type of data to load
         *
         * @return  The {@link CoreLoad}
         */
        public static <E, D> CoreLoad<E, D> getExistingLoader() {
            return getExistingLoader(null, null);
        }

        /**
         * Returns the {@link CoreLoad} kept in the current {@link ViewModel} (mostly for screen
         * orientation changes handling); also handles swipe-to-refresh (if available).
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)});
         *        null can be provided (if one and only BaseViewModel available)
         *
         * @param activity
         *        The Activity (or null for the current one)
         *
         * @param <E>
         *        The type of error (if any)
         *
         * @param <D>
         *        The type of data to load
         *
         * @return  The {@link CoreLoad}
         */
        public static <E, D> CoreLoad<E, D> getExistingLoader(String viewModelKey, Activity activity) {
            if (viewModelKey == null) viewModelKey = BaseViewModel.DEFAULT_KEY;
            if (activity     == null) activity     = Utils.getCurrentActivity();

            final BaseViewModel<D> viewModel = BaseViewModel.get(
                    BaseViewModel.cast(activity, null), viewModelKey);
            if (viewModel == null) return null;

            final Collection<CoreLoad<?, ?>> coreLoads = viewModel.getCoreLoads();
            if (coreLoads == null || coreLoads.size() == 0) return null;

            @SuppressWarnings("unchecked")
            final CoreLoad<E, D> coreLoad = (CoreLoad<E, D>) coreLoads.iterator().next();

            //noinspection rawtypes
            CoreLoadBuilder.setAdapter(CoreLoadBuilder.getList(),
                    (BaseResponseLoaderWrapper) CoreLoadHelper.getLoader(coreLoad));

            if (coreLoads.size() > 1)
                CoreLogger.logError("only 1st CoreLoads handled, CoreLoads qty: " + coreLoads.size());

            handleSwipeToRefresh(coreLoad, activity);

            return coreLoad;
        }

        /**
         * Returns the {@link CoreLoad} kept in the current {@link ViewModel} (mostly for screen
         * orientation changes handling); also handles swipe-to-refresh (if available). Intended to
         * use for avoid memory leaks ('cause of anonymous Rx, OnItemClickListener and LoaderCallbacks).
         *
         * @param viewModelKey
         *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)});
         *        null can be provided (if one and only BaseViewModel available)
         *
         * @param activity
         *        The Activity (or null for the current one)
         *
         * @param onItemClickListener
         *        The {@link OnItemClickListener} (or null for no item's click handling)
         *
         * @param rx
         *        The {@link SubscriberRx} component (or null if not used)
         *
         * @param isRxSingle
         *        {@code true} if {@code Rx} either emits one value only or an error notification,
         *        {@code false} otherwise; null for default value
         *
         * @param loaderCallbacks
         *        The {@link LoaderCallbacks} component (or null if not used)
         *
         * @param <E>
         *        The type of error (if any)
         *
         * @param <D>
         *        The type of data to load
         *
         * @return  The {@link CoreLoad}
         */
        @SuppressWarnings({"unchecked", "rawtypes"})          // Rx, LoaderCallbacks
        public static <E, D> CoreLoad<E, D> getExistingLoader(final String viewModelKey, final Activity activity,
                                                              final OnItemClickListener  onItemClickListener,
                                                              final SubscriberRx<D>      rx,
                                                                    Boolean              isRxSingle,
                                                              final LoaderCallbacks      loaderCallbacks) {
            final CoreLoad<E, D> coreLoad = getExistingLoader(viewModelKey, activity);
            if (coreLoad == null) return null;

            if (isRxSingle == null) isRxSingle = getSwipeView(activity) == null;

            final List<BaseLoaderWrapper<?>> loaders = coreLoad.getLoaders();
            if (loaders != null) for (final BaseLoaderWrapper<?> loader: loaders) {
                final BaseRecyclerViewAdapter adapter = loader.getRecyclerViewAdapter();
                if (adapter != null) adapter.setOnItemClickListener(onItemClickListener);

                if (loader instanceof BaseResponseLoaderWrapper) {
                    final BaseResponseLoaderWrapper<?, ?, ?, ?> loaderCasted =
                            (BaseResponseLoaderWrapper<?, ?, ?, ?>) loader;
                    final LoaderRx<?, ?, ?> rxCurrent = loaderCasted.getRx();
                    if (rxCurrent != null) rxCurrent.cleanup();
                    loaderCasted.setRx(getRx(rx, isRxSingle));

                    loaderCasted.setLoaderCallbacks(loaderCallbacks);
                }
                else {
                    final String error = " ignored in getExistingLoader(): loader is not " +
                            "BaseResponseLoaderWrapper, but " + CoreLogger.getDescription(loader);
                    if (rx              != null) CoreLogger.logError("Rx"              + error);
                    if (loaderCallbacks != null) CoreLogger.logError("LoaderCallbacks" + error);
                }
            }
            return coreLoad;
        }
    }
}
