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

package akha.yakhont.demo;

import akha.yakhont.demo.gui.Bubbles;
import akha.yakhont.demo.gui.SlideShow;
import akha.yakhont.demo.model.Beer;
import akha.yakhont.demo.model.BeerDefault;
import akha.yakhont.demo.retrofit.LocalOkHttpClient;
import akha.yakhont.demo.retrofit.LocalOkHttpClient2;
import akha.yakhont.demo.retrofit.RetrofitApi;
import akha.yakhont.demo.retrofit.Retrofit2Api;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefault;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeToRefreshWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.retrofit.Retrofit;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;
import akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitCoreLoadBuilder;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.BindingAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.gson.reflect.TypeToken;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainFragment extends Fragment implements MeasuredViewAdjuster {

    private       CoreLoad<? extends Throwable, ?>              mCoreLoad;
    @SuppressWarnings("unused")
    private       boolean                                       mNotDisplayLoadingErrors;

    private       LocalOkHttpClient                             mOkHttpClient;
    private       LocalOkHttpClient2                            mOkHttpClient2;

    private       Retrofit <RetrofitApi,  List<BeerDefault>>    mRetrofit;
    private       Retrofit2<Retrofit2Api, List<Beer>>           mRetrofit2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return initGui(inflater, container);
    }

    // every loader should have unique Retrofit object; don't share it with other loaders
    private void createRetrofit(Context context) {

        // local JSON client, so URL doesn't matter
        String url = "http://localhost/";

        if (getMainActivity().isRetrofit2()) {
            mRetrofit2      = new Retrofit2<>();
            mOkHttpClient2  = new LocalOkHttpClient2(context, mRetrofit2);
            mRetrofit2.init(Retrofit2Api.class, url, mOkHttpClient2);

            mOkHttpClient2.getLocalOkHttpClientHelper().setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
        }
        else {
            mRetrofit       = new Retrofit<>();
            mOkHttpClient   = new LocalOkHttpClient(context);
            mRetrofit.init(RetrofitApi.class, mRetrofit.getDefaultBuilder(url).setClient(mOkHttpClient));

            mOkHttpClient .getLocalOkHttpClientHelper().setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
        }

        // for normal HTTP requests it's much simpler - just something like this:
//      mRetrofit2 = new Retrofit2<Retrofit2Api, List<Beer>>().init(Retrofit2Api.class, "http://...");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initGui(savedInstanceState);

        createRetrofit(getActivity());

        initRx();                            // optional

        if (getMainActivity().isRetrofit2()) // example with Data Binding Library support (POJO Beer)
            createLoaderForRetrofit2();
        else                                 // example without Data Binding Library support (POJO BeerDefault)
            createLoaderForRetrofit();       // just for historical reasons

        // uncomment to clear cache
//      if (savedInstanceState == null) akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.clearCache(mCoreLoad);
// or                                   Utils.clearCache("your_table_name");

        // Swipe-To-Refresh handling (optional, remove here and in xml layout if not needed)
        registerSwipeToRefresh();

        startLoading(savedInstanceState, false);
    }

    private void startLoading(Bundle savedInstanceState, boolean byUserRequest) {

        updateGuiAndSetPartToLoad(byUserRequest);

        mCoreLoad.setGoBackOnCancelLoading(!byUserRequest);

        mCoreLoad.start(getActivity(), getLoadParameters(
                byUserRequest ? mCheckBoxForce.isChecked(): savedInstanceState != null,
                !byUserRequest, mCheckBoxMerge.isChecked()));
    }

    private LoadParameters getLoadParameters(boolean forceCache, boolean noProgress, boolean merge) {
        return new LoadParameters.Builder().setForceCache(forceCache).setNoProgress(noProgress)
                .setMerge(merge).setNoErrors(mNotDisplayLoadingErrors).create();
    }

    private void createLoaderForRetrofit2() {
        //noinspection Convert2Diamond
        mCoreLoad = new Retrofit2CoreLoadBuilder<List<Beer>, Retrofit2Api>(mRetrofit2) /* {

                // usage examples ('raw calls' means - without default Yakhont pre- and postprocessing)
                @Override
                public void makeRequest(@NonNull retrofit2.Callback<List<Beer>> callback) {
                    // typical call for Retrofit2 API (Rx2 / Rx / Call) - but for such simple calls
                    //   it's better to use 'setRequester(Retrofit2Api::getDataRx)'
//                  getApi(callback).getDataRx();

                    // raw call for Retrofit2 API with Rx2   ('getApi()' takes null)
                    //   it's exactly the same as 'setRequester(Retrofit2Api::getDataRx)' below
                    //   and 'getApi(callback).getDataRx()' above
                    getRx2DisposableHandler().add(akha.yakhont.technology.rx.Rx2.handle(
                            getApi(null).getDataRx(), getRxWrapper(callback)));

                    // raw call for Retrofit2 API without Rx ('getApi()' takes null)
//                  getApi(null).getData("some parameter").enqueue(callback);
                }
            } */

            // for raw calls you should set cache table name and data type
//          .setTableName("your_cache_table_name")
//          .setType(new TypeToken<List<Beer>>() {}.getType())

            .setRequester(Retrofit2Api::getDataRx)
// or       .setRequester(retrofit2Api -> retrofit2Api.getData("some parameter"))

            // recommended way - but default binding also works (see below in Retrofit 1 example)
            .setDataBinding(BR.beer)

            // it's optional - but sometimes you need to provide some customization here
            .setLoaderCallback(MainFragment.this::onLoadFinishedDataBinding)
/*
            // just an example: it does exactly the same as call above, but provides more options to customize
            .setLoaderCallbacks(
                    new akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallbacks<Throwable, List<Beer>>() {
                @Override
                public void onLoadFinished(List<Beer> data, Source source) {
                    MainFragment.this.onLoadFinished(data, source);
                }

                @Override
                public void onLoadError(Throwable error, Source source) {
                    // customize your error reporting here (e.g. set 'mNotDisplayLoadingErrors = true'
                    //   to suppress Toast-based default error reporting)
                    // also, you can do such customization in 'Rx.onError()' below
                }
            })
*/
            .setRx(mRxRetrofit2)                                    // optional

            .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

            .setProgress(new ProgressDemo())                        // custom data loading progress screen
/* or something like this:
            .setProgress(new java.util.concurrent.Callable<akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress>() {
                @Override
                public akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress call() {
                    // if you're implementing your own cancel confirmation logic, you should to call
                    //   'LiveDataDialog.cancel(Activity)' to cancel data loading
                    return new akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress() {
                        @Override public void setText(String text)       {...}
                        @Override public void show()                     {...}
                        @Override public void hide()                     {...}
                        @Override public void confirm(Activity activity) {...}
                    };
                }
            })
*/
            // switch default cache off
//          .setNoCache(true)

            .create();
    }

    private void createLoaderForRetrofit() {
        //noinspection Anonymous2MethodRef,Convert2Lambda,Convert2Diamond
        mCoreLoad = new RetrofitCoreLoadBuilder<List<BeerDefault>, RetrofitApi>(mRetrofit) /* {

                // 'setRequester(...)' doesn't work for Retrofit 1
                @Override
                public void makeRequest(@NonNull retrofit.Callback<List<BeerDefault>> callback) {
                    getApi(callback).getData(callback);
                }
            } */

            // this is not related to Retrofit 1 - just demo without overriding makeRequest(callback):
            //   Retrofit API method to call will be selected based on the method return type
            //
            // Note: default requester for Retrofit 1 doesn't support Rx -
            //   please consider to switch to Retrofit 2 (or override makeRequest)
            .setType(new TypeToken<List<BeerDefault>>() {}.getType())

            // this is not related to Retrofit 1 - just demo for default binding (reflection based)
            .setListItem(R.layout.grid_item_default)

            .setLoaderCallback(MainFragment.this::onLoadFinished)

            .setViewBinder(new ViewBinder() {                       // data binding (optional too)
                @Override
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    return MainFragment.this.setViewValue(view, data, textRepresentation);
                }
            })

            .setRx(mRxRetrofit)                                     // optional

            .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

            .setProgress(new ProgressDemo())                        // custom data loading progress screen

            .create();
    }

    private void onLoadFinished(List<?> data, Source source) {

        setBubblesState(false);

        if (data != null) mGridView.startLayoutAnimation();

        mLastSource = source;
    }

    private void onLoadFinishedDataBinding(List<Beer> data, Source source) {

        for (Beer beer: data)
            beer.setCacheInfo(getVisibility());

        onLoadFinished(data, source);
    }

    @SuppressWarnings("UnusedParameters")
    private boolean setViewValue(View view, Object data, String textRepresentation) {

        switch (view.getId()) {

            case R.id._id:
                view.setVisibility(getVisibility());
                return ViewBinder.VIEW_BOUND;                   // switch off default view binding

            case R.id.image:
                setImageUrl((ImageView) view, textRepresentation);
                return ViewBinder.VIEW_BOUND;
        }
        
        return !ViewBinder.VIEW_BOUND;                          // default view binding will be applied
    }

    private int getVisibility() {
        return mCheckBoxForce.isChecked() ? View.VISIBLE: View.INVISIBLE;
    }

    @SuppressWarnings("WeakerAccess")
    @BindingAdapter("android:src")
    public static void setImageUrl(ImageView view, String data) {

        int pos = data.indexOf("img_");

        view.setTag(data.substring(pos + 4, pos + 6));
        view.setImageURI(Uri.parse(data));
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    /////////// Rx handling (optional)

    private       RetrofitRx <List<BeerDefault>>        mRxRetrofit;
    private       Retrofit2Rx<List<Beer>>               mRxRetrofit2;

    // unsubscribe goes automatically
    @SuppressWarnings("ConstantConditions")
    private void initRx() {

        boolean singleRx = false;     // don't change

        if (getMainActivity().isRetrofit2()) {
            mRxRetrofit2 = new Retrofit2Rx<>(getMainActivity().isRxJava2(), singleRx);

            mRxRetrofit2.subscribeSimple(new SubscriberRx<List<Beer>>() {
                @Override
                public void onNext(List<Beer> data) {
                    logRx("Retrofit2", data);
                }

                @Override
                public void onError(Throwable throwable) {
                    logRx("Retrofit2", throwable);
                }
            });
        }
        else {
            mRxRetrofit = new RetrofitRx<>(getMainActivity().isRxJava2(), singleRx);

            mRxRetrofit.subscribeSimple(new SubscriberRx<List<BeerDefault>>() {
                @Override
                public void onNext(List<BeerDefault> data) {
                    logRx("Retrofit", data);
                }

                @Override
                public void onError(Throwable throwable) {
                    logRx("Retrofit", throwable);
                }
            });
        }
    }

    private void logRx(String info, List<?> data) {
        Log.w("MainFragment", "LoaderRx (" + info + "): onNext, data == " + (data == null ? "null":
                Arrays.deepToString(data.toArray())));
    }

    private void logRx(String info, Throwable throwable) {
        Log.e("MainFragment", "LoaderRx (" + info + "): onError, error == " + throwable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // below is GUI stuff only

    private static final int            EMULATED_NETWORK_DELAY          = 3;     // seconds
    private static final int            PARTS_QTY                       = 3;

    private static final String         ARG_PART_COUNTER                = "part_counter";

    private              AbsListView    mGridView;
    private              CheckBox       mCheckBoxForce, mCheckBoxMerge;

    private        final SlideShow      mSlideShow                      = new SlideShow();

    private              int            mPartCounter;
    private              Source         mLastSource;

    private              Rect           mSlideRect;

    private final View.OnClickListener  mListener                       = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            registerSwipeToRefresh();

            mCheckBoxForce.setEnabled(!mCheckBoxMerge.isChecked());
            mCheckBoxMerge.setEnabled(!mCheckBoxForce.isChecked());
        }
    };

    private void registerSwipeToRefresh() {
        SwipeToRefreshWrapper.register(getActivity(), R.id.swipeContainer, mCoreLoad.getLoaders(),
                getLoadParameters(mCheckBoxForce.isChecked(), false, mCheckBoxMerge.isChecked()));
    }

    // just a boilerplate code
    private View initGui(LayoutInflater inflater, ViewGroup container) {

        View mainView       = inflater.inflate(R.layout.fragment_main, container, false);

        mGridView           = mainView.findViewById(R.id.grid);

        mCheckBoxMerge      = mainView.findViewById(R.id.flag_merge);
        mCheckBoxForce      = mainView.findViewById(R.id.flag_force);

        mainView.findViewById(R.id.btn_load).setOnClickListener(
                view -> startLoading(null, true));

        mCheckBoxForce.setOnClickListener(mListener);
        mCheckBoxMerge.setOnClickListener(mListener);

        Utils.onAdjustMeasuredView(this, mainView.findViewById(R.id.container));

        mSlideShow.init(mainView);

        return mainView;
    }

    private void initGui(Bundle savedInstanceState) {

        if (savedInstanceState != null && savedInstanceState.keySet().contains(ARG_PART_COUNTER))
            mPartCounter = savedInstanceState.getInt(ARG_PART_COUNTER);

        mSlideShow.init(this);

        Bubbles.init(Objects.requireNonNull(getActivity()));
    }

    private void updateGuiAndSetPartToLoad(boolean byUserRequest) {

        if (byUserRequest)
            setBubblesState(true);
        else {
            mLastSource  = Source.NETWORK;      // even if mCheckBoxForce.isChecked()
            mPartCounter = PARTS_QTY - 1;
        }

        if (mLastSource == Source.NETWORK) {
            if (++mPartCounter == PARTS_QTY) mPartCounter = 0;

            String scenario = "part" + mPartCounter;

            if (getMainActivity().isRetrofit2())
                mOkHttpClient2.getLocalOkHttpClientHelper().setScenario(scenario);
            else
                mOkHttpClient .getLocalOkHttpClientHelper().setScenario(scenario);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {

        savedInstanceState.putInt(ARG_PART_COUNTER, mPartCounter);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onDestroyView() {

        mSlideShow.cleanUp();
        Bubbles.cleanUp();

        super.onDestroyView();
    }
    
    public void onSlideShow(boolean isStarted) {
        getMainActivity().setSlideShow(isStarted ? mSlideShow: null);
        setBubblesState(isStarted);
    }

    private void setBubblesState(boolean cancel) {
        Bubbles.setState(cancel, false);
    }

    @SuppressWarnings("unused")
    @Override
    public void adjustMeasuredView(View view) {
        mSlideRect = new Rect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    public Rect getSlideRect() {
        return mSlideRect;
    }

    private class ProgressDemo extends ProgressDefault {

        private ProgressDialogFragment  mProgress;

        @Override
        public void setText(String text) {
        }

        @Override
        public void show() {
            mProgress = new ProgressDialogFragment();
            mProgress.show(Objects.requireNonNull(getActivity()).getSupportFragmentManager(), "ProgressDialogFragment");
        }

        @Override
        public void hide() {
            super.hide();

            if (mProgress == null) return;

            mProgress.dismiss();
            mProgress = null;
        }

        @Override
        public void confirm(Activity activity) {
            if (mProgress != null && mProgress.isConfirm()) super.confirm(activity);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class ProgressDialogFragment extends DialogFragment {

        private static BitmapDrawable   sBackground;

        private        CheckBox         mConfirm;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Activity activity = getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(Objects.requireNonNull(activity));

            @SuppressLint("InflateParams")
            View view = LayoutInflater.from(activity).inflate(R.layout.progress, null);
            ((TextView) view.findViewById(R.id.progress_message)).setText(
                    LiveDataDialog.getInfoText(R.string.table_desc_beers));

            if (sBackground == null) {
                DisplayMetrics dm       = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

                Resources resources     = activity.getResources();
                sBackground             = new BitmapDrawable(resources,
                        akha.yakhont.demo.gui.Utils.decodeBitmap(
                                resources, R.drawable.img_progress, dm.heightPixels, dm.widthPixels));
            }
            ((ImageView) view.findViewById(R.id.progress_background)).setImageDrawable(sBackground);

            mConfirm = view.findViewById(R.id.progress_confirm);

            return builder.setView(view).create();
        }

        private boolean isConfirm() {
            return mConfirm.isChecked();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            if (isConfirm()) return;

            Utils.showToast(R.string.yakhont_loader_cancelled, !Utils.SHOW_DURATION_LONG);
            LiveDataDialog.cancel(getActivity());
        }
    }
}
