/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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
import akha.yakhont.Core.Utils.CoreLoadHelper;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.Core.Utils.RetainDialogFragment;
import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefaultDialog;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
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
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.BindingAdapter;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.google.gson.reflect.TypeToken;

public class MainFragment extends Fragment implements MeasuredViewAdjuster {

    // if you have more than one loader in your Activity / Fragment / Service -
    //   please provide unique ViewModel keys
//  private static final String                                  DEMO_VIEWMODEL_KEY         = "yakhont_demo_viewmodel_key";

    private static final int                                     EMULATED_NETWORK_DELAY     = 7;

    private       CoreLoad<? extends Throwable, ?>               mCoreLoad;
    @SuppressWarnings("unused")
    private       boolean                                        mNotDisplayLoadingErrors;

    private       LocalOkHttpClient                              mOkHttpClient;
    private       LocalOkHttpClient2                             mOkHttpClient2;

    private       Retrofit <RetrofitApi,  List<BeerDefault>>     mRetrofit;
    private       Retrofit2<Retrofit2Api, List<Beer>>            mRetrofit2;

    public MainFragment() {
        mGuiHelper = new DemoGuiHelper();

        setRetainInstance(true);
    }

    // every loader should have unique Retrofit object; don't share it with other loaders
    private void createRetrofit(Context context) {
        String url = "http://localhost/";       // local JSON client, so URL doesn't matter

        if (getMainActivity().isRetrofit2()) {
            mRetrofit2      = new Retrofit2<>();
            mOkHttpClient2  = new LocalOkHttpClient2(context, mRetrofit2);
            mRetrofit2.init(Retrofit2Api.class, url, mOkHttpClient2);
        }
        else {
            mRetrofit       = new Retrofit<>();
            mOkHttpClient   = new LocalOkHttpClient(context);
            mRetrofit.init(RetrofitApi.class, mRetrofit.getDefaultBuilder(url).setClient(mOkHttpClient));
        }

        // for normal HTTP requests it's much simpler - just something like this:
//      mRetrofit2 = new Retrofit2<Retrofit2Api, List<Beer>>().init(Retrofit2Api.class, "http://...");
    }

    private void setEmulatedNetworkDelay() {
        if (getMainActivity().isRetrofit2())
            mOkHttpClient2.setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
        else
            mOkHttpClient .setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mGuiHelper.initGui();

        if (mCoreLoad != null)               // handling screen orientation changes
            mGuiHelper.mGridView.setAdapter(CoreLoadHelper.getLoader(mCoreLoad).getListAdapter());
        else
            init();

        // Swipe-To-Refresh handling (optional, remove here and in xml layout if not needed)
        mGuiHelper.registerSwipeToRefresh();
    }

    private void init() {
        createRetrofit(getActivity());

        initRx();                            // optional

        if (getMainActivity().isRetrofit2()) // example with Data Binding Library support (POJO Beer)
            createLoaderForRetrofit2();
        else                                 // example without Data Binding Library support (POJO BeerDefault)
            createLoaderForRetrofit();       // just for historical reasons

        // uncomment to clear cache
//      if (savedInstanceState == null) akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.clearCache(mCoreLoad);
// or                                   Utils.clearCache("your_table_name");

        startLoading(false);

        // emulates network delay to demonstrate progress customization
        mGuiHelper.mGridView.postDelayed(this::setEmulatedNetworkDelay, 500);
    }

    private void startLoading(boolean byUserRequest) {
        mGuiHelper.updateGuiAndSetPartToLoad(byUserRequest);

        mCoreLoad.start(getActivity(), getLoadParameters(mGuiHelper.mCheckBoxForce.isChecked(),
                !byUserRequest, mGuiHelper.mCheckBoxMerge.isChecked()));
    }

    private LoadParameters getLoadParameters(boolean forceCache, boolean noProgress, boolean merge) {
        return new LoadParameters.Builder(null).setForceCache(forceCache)
                .setNoProgress(noProgress).setMerge(merge).setNoErrors(mNotDisplayLoadingErrors).create();
    }

    private void createLoaderForRetrofit2() {
        Retrofit2CoreLoadBuilder<List<Beer>, Retrofit2Api> builder = new Retrofit2CoreLoadBuilder<>(mRetrofit2);

        mCoreLoad = builder
                .setRequester(Retrofit2Api::getDataRx)
// or           .setRequester(retrofit2Api -> retrofit2Api.getData("some parameter"))
/* or
                // usage examples ('raw' means: without default Yakhont pre- and postprocessing)
                .setRequesterRaw(callback -> {
                    // typical call for Retrofit2 (Rx2 / Rx / Call) - but for such simple calls
                    //   it's better to use 'setRequester(Retrofit2Api::getDataRx)'
//                  builder.getApi(callback).getDataRx();

                    // raw call ('getApi()' takes null) for Retrofit2 with Rx2
                    //   it's exactly the same as 'setRequester(Retrofit2Api::getDataRx)' below
                    //   and 'getApi(callback).getDataRx()' above
                    builder.getRx2DisposableHandler().add(
                            akha.yakhont.technology.rx.Rx2.handle(builder.getApi(null).getDataRx(),
                                    Retrofit2.getRxWrapper(callback)));

                    // raw call ('getApi()' takes null) for Retrofit2 with Rx
                    //   don't forget to set MainActivity.USE_RX_JAVA_2 = false
//                  akha.yakhont.technology.rx.Rx.getRxSubscriptionHandler(builder.getRx()).add(
//                          akha.yakhont.technology.rx.Rx.handle(builder.getApi(null).getDataOldRx(),
//                                  Retrofit2.getRxWrapper(callback)));

                    // raw call ('getApi()' takes null) for Retrofit2 without Rx
//                  builder.getApi(null).getData("some parameter").enqueue(callback);
                })
*/
                // for raw calls you should set cache table name and data type
//              .setTableName("your_cache_table_name")
//              .setType(new TypeToken<List<Beer>>() {}.getType())

                // recommended way - but default binding also works (see below in non-Retrofit2 example)
                .setDataBinding(BR.beer)

//              .setBaseViewModelKey(DEMO_VIEWMODEL_KEY)

                // it's optional too - but sometimes you need to provide some customization here
                .setLoaderCallback(MainFragment.this::onLoadFinishedDataBinding)
/* or
                // just an example: it does exactly the same as call above, but provides more options to customize
                .setLoaderCallbacks(new akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper
                        .LoaderCallbacks<Throwable, List<Beer>>() {
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
                .setRx(mRxRetrofit2)                            // optional

                .setProgress(new ProgressDemo())                // custom data loading progress screen (optional)
/* or something like this:
                // if you're implementing your own cancel confirmation logic, you should call
                //   'LiveDataDialog.cancel(Activity)' to cancel data loading
                .setProgress(() -> new LiveDataDialog.Progress() {
                    @Override public void setText(String text)                  {...}
                    @Override public void show()                                {...}
                    @Override public void hide()                                {...}
                    @Override public void confirm(Activity activity, View view) {...}
                })
*/
                // switch default cache off
//              .setNoCache(true)

                .setDescriptionId(R.string.table_desc_beers)    // data description for GUI (optional)

                .create();
    }

    private void createLoaderForRetrofit() {
        RetrofitCoreLoadBuilder<List<BeerDefault>, RetrofitApi> builder = new RetrofitCoreLoadBuilder<>(mRetrofit);

        mCoreLoad = builder
/*              .setRequesterRaw(callback -> {    // 'setRequester(...)' doesn't work for old Retrofit
                    builder.getApi(callback).getData(callback);
                })
*/
                // this is not related to Retrofit - just demo without 'setRequesterRaw()' call:
                //   Retrofit method to call will be selected based on the method return type
                //
                // Note: default requester for old Retrofit doesn't support Rx -
                //   please consider to switch to Retrofit2 (or override makeRequest)
                .setType(new TypeToken<List<BeerDefault>>() {}.getType())

                // this is not related to Retrofit - just demo for default binding (reflection based)
                .setListItem(R.layout.grid_item_default)

                .setViewBinder(MainFragment.this::setViewValue) // data binding (optional)

//              .setBaseViewModelKey(DEMO_VIEWMODEL_KEY)

                .setLoaderCallback(MainFragment.this::onLoadFinished)

                .setRx(mRxRetrofit)                             // optional too

                .setDescriptionId(R.string.table_desc_beers)    // data description for GUI (optional)

                .setProgress(new ProgressDemo())                // custom data loading progress screen (optional)

                .create();
    }

    private void onLoadFinished(List<?> data, Source source) {
        mGuiHelper.setBubblesState(false);

        if (data != null) mGuiHelper.mGridView.startLayoutAnimation();

        mGuiHelper.mLastSource = source;
    }

    private void onLoadFinishedDataBinding(List<Beer> data, Source source) {
        if (data != null)
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
        return mGuiHelper.mCheckBoxForce.isChecked() ? View.VISIBLE: View.INVISIBLE;
    }

    @SuppressWarnings("WeakerAccess")
    @BindingAdapter("android:src")
    public static void setImageUrl(ImageView view, String data) {
        int pos = data.indexOf("img_");

        view.setTag(data.substring(pos + 4, pos + 6));
        view.setImageURI(Uri.parse(data));
    }

    private static MainActivity getMainActivity(final Fragment fragment) {
        return Objects.requireNonNull /* should always happen */ ((MainActivity) fragment.getActivity());
    }

    private MainActivity getMainActivity() {
        return getMainActivity(this);
    }

    /////////// Rx handling (optional)

    private       RetrofitRx <List<BeerDefault>>        mRxRetrofit;
    private       Retrofit2Rx<List<Beer>>               mRxRetrofit2;

    // unsubscribe goes automatically
    @SuppressWarnings("ConstantConditions")
    private void initRx() {
        boolean singleRx = false;

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
        CoreLogger.log("LoaderRx (" + info + "): onNext, data == " + (data == null ? "null":
                Arrays.deepToString(data.toArray())));
    }

    private void logRx(String info, Throwable throwable) {
        CoreLogger.log("LoaderRx (" + info + ")", throwable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // below is demo GUI stuff only

    private        final DemoGuiHelper  mGuiHelper;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return mGuiHelper.initGui(inflater, container);
    }

    @Override
    public void onDestroyView() {
        mGuiHelper.mSlideShow.cleanUp();
        Bubbles.cleanUp();

        super.onDestroyView();
    }

    @SuppressWarnings("unused")
    @Override
    public void adjustMeasuredView(View view) {
        mGuiHelper.mSlideRect = new Rect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    public DemoGuiHelper getDemoGuiHelper() {
        return mGuiHelper;
    }

    public class DemoGuiHelper {

        private          AbsListView    mGridView;
        private          CheckBox       mCheckBoxForce, mCheckBoxMerge;

        private    final SlideShow      mSlideShow                      = new SlideShow();

        private          Source         mLastSource;

        private          Rect           mSlideRect;

        private    final View.OnClickListener
                                        mListener                       = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerSwipeToRefresh();

                mCheckBoxForce.setEnabled(!mCheckBoxMerge.isChecked());
                mCheckBoxMerge.setEnabled(!mCheckBoxForce.isChecked());
            }
        };

        private void registerSwipeToRefresh() {
            SwipeToRefreshWrapper.register(getActivity(), R.id.swipeContainer, mCoreLoad.getLoaders(),
                    getLoadParameters(
                            mCheckBoxForce.isChecked(), false, mCheckBoxMerge.isChecked()));
        }

        // just a boilerplate code
        private View initGui(LayoutInflater inflater, ViewGroup container) {
            View mainView       = inflater.inflate(R.layout.fragment_main, container, false);

            mGridView           = mainView.findViewById(R.id.grid);

            mCheckBoxMerge      = mainView.findViewById(R.id.flag_merge);
            mCheckBoxForce      = mainView.findViewById(R.id.flag_force);

            mainView.findViewById(R.id.btn_load).setOnClickListener(
                    view -> startLoading(true));

            mCheckBoxForce.setOnClickListener(mListener);
            mCheckBoxMerge.setOnClickListener(mListener);

            Utils.onAdjustMeasuredView(MainFragment.this, mainView.findViewById(R.id.container));

            mSlideShow.init(mainView);

            return mainView;
        }

        private void initGui() {
            mSlideShow.init(MainFragment.this);

            Bubbles.init(getMainActivity());
        }

        private void updateGuiAndSetPartToLoad(boolean byUserRequest) {
            if (byUserRequest)
                setBubblesState(true);
            else
                mLastSource  = Source.NETWORK;      // even if mCheckBoxForce.isChecked()

            if (mLastSource == Source.NETWORK) {
                ListAdapter adapter = mGridView.getAdapter();
                int size = adapter.getCount();

                int partCounter = 0;
                if (size > 0) {
                    Object item = adapter.getItem(size - 1);
                    String img = item instanceof Beer ? ((Beer) item).getImage():
                            // 'cause of default binding (reflection based) in non-Retrofit2 demo
                            (String) ((ContentValues) item).get("image");
                    int pos = img.indexOf("img_");
                    switch (img.substring(pos + 4, pos + 6)) {
                        case "05":
                            partCounter = 1;
                            break;
                        case "11":
                            partCounter = 2;
                            break;
                    }
                }
                String scenario = "part" + partCounter;

                if (getMainActivity().isRetrofit2())
                    mOkHttpClient2.getLocalOkHttpClientHelper().setScenario(scenario);
                else
                    mOkHttpClient .getLocalOkHttpClientHelper().setScenario(scenario);
            }
        }

        public void onSlideShow(boolean isStarted) {
            getMainActivity().setSlideShow(isStarted ? mSlideShow: null);
            setBubblesState(isStarted);
        }

        private void setBubblesState(boolean cancel) {
            Bubbles.setState(cancel, false);
        }

        public Rect getSlideRect() {
            return mSlideRect;
        }
    }

    // we need such class only to handle the 'confirm' flag - otherwise use 'ProgressDefaultDialog'
    private static class ProgressDemo extends ProgressDefaultDialog {

        private ProgressDemo() {
            super(ProgressDialogFragment.class);
        }

        @Override
        public boolean confirm(Activity activity, View view) {
            ProgressDialogFragment progress = (ProgressDialogFragment) getDialog();
            if (progress != null && progress.mConfirm.isChecked()) super.confirm(activity, view);
            return true;
        }
    }

    // RetainDialogFragment prevents DialogFragment to be destroyed after screen orientation changed
    // (actually it's a Google API bug workaround)
    @SuppressWarnings("WeakerAccess")
    public static class ProgressDialogFragment extends RetainDialogFragment {

        private static BitmapDrawable   sBackground;
        private        CheckBox         mConfirm;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Activity activity = getMainActivity(this);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            @SuppressLint("InflateParams")
            View view = LayoutInflater.from(activity).inflate(R.layout.progress, null);
            ((TextView) view.findViewById(R.id.progress_message)).setText(
                    LiveDataDialog.getInfoText(R.string.table_desc_beers));

            if (sBackground == null) {          // cache the background image
                DisplayMetrics dm       = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

                Resources resources     = activity.getResources();
                sBackground             = new BitmapDrawable(resources,
                        akha.yakhont.demo.gui.Utils.decodeBitmap(
                                resources, R.drawable.img_progress, dm.heightPixels, dm.widthPixels));
            }
            ((ImageView) view.findViewById(R.id.progress_background)).setImageDrawable(sBackground);

            mConfirm = view.findViewById(R.id.progress_confirm);

//          normally such call should be enough - but here we handle the 'confirm' flag, so see below...
//          return ProgressDefaultDialog.handle(builder.setView(view).create(), view);

            return ProgressDefaultDialog.handle(builder.setView(view).create(), () -> {
                if (mConfirm.isChecked()) {
                    BaseViewModel.get(BaseViewModel.DEFAULT_KEY).getData().confirm(getActivity(), view);
//                  BaseViewModel.get(DEMO_VIEWMODEL_KEY       ).getData().confirm(getActivity(), view);
                    return true;
                }
                Utils.showToast(R.string.yakhont_loader_cancelled, Toast.LENGTH_LONG);
                LiveDataDialog.cancel(getActivity());
                return false;
            });
        }
    }
}
