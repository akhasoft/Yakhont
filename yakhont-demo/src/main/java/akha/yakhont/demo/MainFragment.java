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
import akha.yakhont.demo.retrofit.Retrofit2Api;
import akha.yakhont.demo.retrofit.RetrofitApi;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.support.technology.retrofit.RetrofitLoaderWrapper.RetrofitCoreLoadBuilder;
import akha.yakhont.support.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;

// for using non-support version of library (android.app.Fragment etc.):
// comment out akha.yakhont.support.loader.* imports above and uncomment ones below

// also, don't forget to change in build.gradle 'yakhont-support' to 'yakhont' (or 'yakhont-full')

/*
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitCoreLoadBuilder;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
*/

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

    private CoreLoad                    mCoreLoad;
    @SuppressWarnings("unused")
    private boolean                     mNotDisplayLoadingErrors;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return initGui(inflater, container);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initGui(savedInstanceState);

        registerSwipeRefresh();     // SwipeRefreshLayout handling (optional)

        initRx();                   // optional

        final TypeToken type = new TypeToken<List<Beer>>() {};

        if (getMainActivity().isRetrofit2())
            //noinspection Anonymous2MethodRef,Convert2Lambda
            mCoreLoad = new Retrofit2CoreLoadBuilder<List<Beer>, Retrofit2Api>(
                    this, type, getMainActivity().getRetrofit2()) /* {

                        @Override
                        public void makeRequest(@NonNull Callback<List<Beer>> callback) {
                            // if the default request is not OK (by some reason),
                            // you can provide your own here, e.g. for Retrofit2 Call:
                            //     getApi().data().enqueue(callback);
                            // or for Rx2 Flowable:
                            //     getRx2DisposableHandler().add(Rx2.handle(getApi().data(), getRxWrapper(callback)));
                            // or for Rx Observable:
                            //     getRxSubscriptionHandler().add(Rx.handle(getApi().data(), getRxWrapper(callback)));
                            // or ...
                        }
                    } */

                    .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

                    // in general, it's an optional callback -
                    //   but normally you'll want to provide some customization here
                    .setLoaderCallback(new Retrofit2CoreLoadBuilder.LoaderCallback<List<Beer>>() {
                        @SuppressWarnings("unused")
                        @Override
                        public void onLoadFinished(List<Beer> data, Source source) {
                            MainFragment.this.onLoadFinished(data, source);
                        }
                        /*
                        @Override
                        public void onLoadError(Throwable error, Source source) {
                            // customize your error reporting here
                            // set 'mNotDisplayLoadingErrors = true' to suppress default
                            //   error reporting (Toast)
                            // also, you can do such customization in Rx onError() below
                        } */
                    })

                    .setViewBinder(new ViewBinder() {                       // data binding (optional too)
                        @Override
                        public boolean setViewValue(View view, Object data, String textRepresentation) {
                            return MainFragment.this.setViewValue(view, data, textRepresentation);
                        }
                    })

                    .setRx(mRxRetrofit2)                                    // optional

                    .create();
        else
            //noinspection Anonymous2MethodRef,Convert2Lambda
            mCoreLoad = new RetrofitCoreLoadBuilder<List<Beer>, RetrofitApi>(
                    this, type, getMainActivity().getRetrofit())

                    // for the moment the default request doesn't support Rx Observables etc. -
                    //   please consider to switch to Retrofit2 (or override makeRequest)

                    .setDescriptionId(R.string.table_desc_beers)            // data description for GUI (optional)

                    // see comment above
                    .setLoaderCallback(new RetrofitCoreLoadBuilder.LoaderCallback<List<Beer>>() {
                        @SuppressWarnings("unused")
                        @Override
                        public void onLoadFinished(List<Beer> data, Source source) {
                            MainFragment.this.onLoadFinished(data, source);
                        }
                    })

                    .setViewBinder(new ViewBinder() {                       // data binding (optional too)
                        @Override
                        public boolean setViewValue(View view, Object data, String textRepresentation) {
                            return MainFragment.this.setViewValue(view, data, textRepresentation);
                        }
                    })

                    .setRx(mRxRetrofit)                                     // optional

                    .create();

        // clear cache (optional)
        if (savedInstanceState == null) BaseResponseLoaderWrapper.clearCache(mCoreLoad);
        // or akha.yakhont.loader.BaseResponse.clearCache(getActivity(), "your_table_name");

        startLoading(savedInstanceState, false);
    }

    private void startLoading(Bundle savedInstanceState, boolean byUserRequest) {
        if (byUserRequest) setBubblesState(true);

        mCoreLoad.setGoBackOnLoadingCanceled(!byUserRequest);

        mCoreLoad.startLoading(new LoadParameters(null, byUserRequest ? mCheckBoxForce.isChecked():
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
        getMainActivity().setScenario("part" + counter);
    }

    private void setNetworkDelay() {
        getMainActivity().setNetworkDelay(EMULATED_NETWORK_DELAY);
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
        setPartToLoad(mPartCounter);

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
