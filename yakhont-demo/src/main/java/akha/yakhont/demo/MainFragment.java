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

package akha.yakhont.demo;

import akha.yakhont.demo.gui.Bubbles;
import akha.yakhont.demo.gui.SlideShow;
import akha.yakhont.demo.model.Beer;
import akha.yakhont.demo.retrofit.LocalJsonClient;
import akha.yakhont.demo.retrofit.LocalJsonClient2;
import akha.yakhont.demo.retrofit.Retrofit2Api;
import akha.yakhont.demo.retrofit.RetrofitApi;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.technology.retrofit.Retrofit;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
// import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.support.technology.retrofit.RetrofitLoaderWrapper.RetrofitCoreLoadBuilder;
import akha.yakhont.support.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;

// for using non-support version of library (android.app.Fragment etc.):
// comment out akha.yakhont.support.loader.* imports above and uncomment ones below

// also, don't forget to change in build.gradle 'yakhont-support' to 'yakhont' (or 'yakhont-full')

/*
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
// import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitCoreLoadBuilder;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
*/

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.reflect.TypeToken;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainFragment extends /* android.app.Fragment */ android.support.v4.app.Fragment
        implements MeasuredViewAdjuster {

    private       CoreLoad                              mCoreLoad;
    @SuppressWarnings("unused")
    private       boolean                               mNotDisplayLoadingErrors;

    private       LocalJsonClient                       mJsonClient;
    private       LocalJsonClient2                      mJsonClient2;

    private       Retrofit <RetrofitApi,  List<Beer>>   mRetrofit;
    private       Retrofit2<Retrofit2Api, List<Beer>>   mRetrofit2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return initGui(inflater, container);
    }

    // every loader should have unique Retrofit object; don't share it with other loaders
    private void createRetrofit(Context context) {

        // local JSON client, so URL doesn't matter
        String url = "http://localhost/";

        if (getMainActivity().isRetrofit2()) {
            mRetrofit2   = new Retrofit2<>();
            mJsonClient2 = new LocalJsonClient2(context, mRetrofit2);
            mRetrofit2.init(Retrofit2Api.class, mRetrofit2.getDefaultBuilder(url).client(mJsonClient2));
        }
        else {
            mRetrofit   = new Retrofit<>();
            mJsonClient = new LocalJsonClient(context);
            mRetrofit.init(RetrofitApi.class, mRetrofit.getDefaultBuilder(url).setClient(mJsonClient));
        }

        // for normal HTTP requests you can do something like this
//      return new Retrofit2<Retrofit2Api, List<Beer>>().init(Retrofit2Api.class, "http://...");

        setPartToLoad(mPartCounter);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initGui(savedInstanceState);

        createRetrofit(getActivity());

        registerSwipeRefresh();     // SwipeRefreshLayout handling (optional)

        initRx();                   // optional

        if (getMainActivity().isRetrofit2())
            //noinspection Convert2Diamond
            mCoreLoad = new Retrofit2CoreLoadBuilder<List<Beer>, Retrofit2Api>(this, mRetrofit2) /* {

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
//                  getApi(null).getData("not used parameter").enqueue(callback);
                }
            } */

            // for raw calls you should set cache table name and data type
//          .setTableName("your_cache_table_name")
//          .setType(new TypeToken<List<Beer>>() {}.getType())

            .setRequester(Retrofit2Api::getDataRx)
// or       .setRequester(retrofit2Api -> retrofit2Api.getData("not used parameter"))

            .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

            // it's optional - but sometimes you need to provide some customization here
            .setLoaderCallback(MainFragment.this::onLoadFinished)
/*
            // just an example: it does exactly the same, but provides more options to customize
            .setLoaderCallback(new Retrofit2CoreLoadBuilder.LoaderCallback<List<Beer>>() {
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
            .setViewBinder(MainFragment.this::setViewValue)         // data binding (optional too)

            .setRx(mRxRetrofit2)                                    // optional

            .create();
        else
            //noinspection Anonymous2MethodRef,Convert2Lambda,Convert2Diamond
            mCoreLoad = new RetrofitCoreLoadBuilder<List<Beer>, RetrofitApi>(this, mRetrofit) /* {

                // 'setRequester(...)' doesn't work for Retrofit 1
                @Override
                public void makeRequest(@NonNull retrofit.Callback<List<Beer>> callback) {
                    getApi(callback).getData(callback);
                }
            } */

            // this is not related to Retrofit 1 - just demo without overriding makeRequest(callback):
            //   Retrofit API method to call will be selected based on the method return type
            //
            // Note: default requester for Retrofit 1 doesn't support Rx -
            //   please consider to switch to Retrofit 2 (or override makeRequest)
            .setType(new TypeToken<List<Beer>>() {}.getType())

            .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

            .setLoaderCallback(MainFragment.this::onLoadFinished)

            // Java 7 style
            .setViewBinder(new ViewBinder() {                       // data binding (optional too)
                @Override
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    return MainFragment.this.setViewValue(view, data, textRepresentation);
                }
            })

            .setRx(mRxRetrofit)                                     // optional

            .create();

        // uncomment to clear cache
//      if (savedInstanceState == null) BaseResponseLoaderWrapper.clearCache(mCoreLoad);
// or   akha.yakhont.loader.BaseResponse.clearCache("your_table_name");

        startLoading(savedInstanceState, false);
    }

    private void startLoading(Bundle savedInstanceState, boolean byUserRequest) {
        if (byUserRequest) setBubblesState(true);

        mCoreLoad.setGoBackOnLoadingCanceled(!byUserRequest);

        mCoreLoad.load(new LoadParameters(null, byUserRequest ? mCheckBoxForce.isChecked():
                savedInstanceState != null, !byUserRequest, mCheckBoxMerge.isChecked(),
                mNotDisplayLoadingErrors, false));
    }

    private void onLoadFinished(List<Beer> data, Source source) {   // called from LoaderManager.LoaderCallbacks.onLoadFinished()
        setBubblesState(false);

        setNetworkDelay();

        if (data   != null)                 mGridView.startLayoutAnimation();
        if (source != Source.NETWORK)       return;

        if (++mPartCounter == PARTS_QTY)    mPartCounter = 0;   // set next part to load
        setPartToLoad(mPartCounter);
    }
    
    @SuppressWarnings("UnusedParameters")
    private boolean setViewValue(View view, Object data, String textRepresentation) {

        switch (view.getId()) {

            case R.id._id:
                if (mCheckBoxForce.isChecked()) {
                    ((TextView) view).setText(getString(R.string.db_id, textRepresentation));
                    view.setVisibility(View.VISIBLE);
                }
                else
                    view.setVisibility(View.INVISIBLE);
                
                return ViewBinder.VIEW_BOUND;                   // switch off default view binding

            case R.id.image:
                int pos = textRepresentation.indexOf("img_");
                view.setTag(textRepresentation.substring(pos + 4, pos + 6));

                ((ImageView) view).setImageURI(Uri.parse(textRepresentation));

                return ViewBinder.VIEW_BOUND;
        }
        
        return !ViewBinder.VIEW_BOUND;                          // default view binding will be applied
    }

    private void setPartToLoad(int counter) {
        String scenario = "part" + counter;

        if (getMainActivity().isRetrofit2())
            mJsonClient2.getLocalJsonClientHelper().setScenario(scenario);
        else
            mJsonClient .getLocalJsonClientHelper().setScenario(scenario);
    }

    private void setNetworkDelay() {
        if (getMainActivity().isRetrofit2())
            mJsonClient2.getLocalJsonClientHelper().setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
        else
            mJsonClient .getLocalJsonClientHelper().setEmulatedNetworkDelay(EMULATED_NETWORK_DELAY);
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    /////////// Rx handling (optional)

    private RetrofitRx <List<Beer>>     mRxRetrofit;
    private Retrofit2Rx<List<Beer>>     mRxRetrofit2;

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

            mRxRetrofit.subscribeSimple(new SubscriberRx<List<Beer>>() {
                @Override
                public void onNext(List<Beer> data) {
                    logRx("Retrofit", data);
                }

                @Override
                public void onError(Throwable throwable) {
                    logRx("Retrofit", throwable);
                }
            });
        }
    }

    private void logRx(String info, List<Beer> data) {
        Log.w("MainFragment", "LoaderRx (" + info + "): onNext, data == " + (data == null ? "null":
                Arrays.deepToString(data.toArray(new Beer[data.size()]))));
    }

    private void logRx(String info, Throwable throwable) {
        Log.e("MainFragment", "LoaderRx (" + info + "): onError, error == " + throwable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // below is GUI stuff only

    private static final int            EMULATED_NETWORK_DELAY          = 3;     // seconds
    private static final int            PARTS_QTY                       = 3;

    private static final String         ARG_PART_COUNTER                = "part_counter";

    private AbsListView                 mGridView;
    private CheckBox                    mCheckBoxForce, mCheckBoxMerge;

    private final SlideShow             mSlideShow                      = new SlideShow();

    private int                         mPartCounter;
    private Rect                        mSlideRect;

    private final View.OnClickListener  mListener                       = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            registerSwipeRefresh();
            mCheckBoxForce.setEnabled(!mCheckBoxMerge.isChecked());
            mCheckBoxMerge.setEnabled(!mCheckBoxForce.isChecked());
        }
    };

    private void registerSwipeRefresh() {
        SwipeRefreshWrapper.register(MainFragment.this, new FragmentData(
                MainFragment.this, R.id.swipeContainer, new LoadParameters(null, mCheckBoxForce.isChecked(),
                false, mCheckBoxMerge.isChecked(), mNotDisplayLoadingErrors, false), null));
    }

    // just a boilerplate code
    private View initGui(LayoutInflater inflater, ViewGroup container) {
        View mainView       = inflater.inflate(R.layout.fragment_main, container, false);

        mGridView           = mainView.findViewById(R.id.grid);

        mCheckBoxForce      = mainView.findViewById(R.id.flag_force);
        mCheckBoxMerge      = mainView.findViewById(R.id.flag_merge);

        //noinspection Convert2Lambda
        mainView.findViewById(R.id.btn_load).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoading(null, true);
            }
        });

        mCheckBoxForce.setOnClickListener(mListener);
        mCheckBoxMerge.setOnClickListener(mListener);

        Utils.onAdjustMeasuredView(this, mainView.findViewById(R.id.container));

        mSlideShow.init(mainView);

        return mainView;
    }

    private void initGui(final Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.keySet().contains(ARG_PART_COUNTER))
            mPartCounter = savedInstanceState.getInt(ARG_PART_COUNTER);

        mSlideShow.init(this);

        Bubbles.init(Objects.requireNonNull(getActivity()));
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
    
    public void onSlideShow(final boolean isStarted) {
        getMainActivity().setSlideShow(isStarted ? mSlideShow: null);
        setBubblesState(isStarted);
    }

    private void setBubblesState(final boolean cancel) {
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
}
