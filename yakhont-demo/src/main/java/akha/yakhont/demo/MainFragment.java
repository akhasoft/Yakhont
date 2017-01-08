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

package akha.yakhont.demo;

import akha.yakhont.demo.gui.Bubbles;
import akha.yakhont.demo.gui.SlideShow;
import akha.yakhont.demo.model.Beer;

import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.LoaderCallback;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;

import akha.yakhont.support.loader.BaseLoader;
import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.support.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.support.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;

// for using non-support version of library (android.app.Fragment etc.):
// comment out akha.yakhont.support.loader.* imports above and uncomment ones below

/*
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper.FragmentData;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
*/

import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Arrays;

import retrofit.client.Response;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

public class MainFragment extends /* android.app.Fragment */ android.support.v4.app.Fragment {

    private CoreLoad                    mCoreLoad;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return initGui(inflater, container);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initGui(savedInstanceState);

        registerSwipeRefresh();     // SwipeRefreshLayout handling (optional)

        mCoreLoad = BaseLoader.simpleInit(this, Beer[].class, mRx, R.string.table_desc_beers, // data description for GUI (optional)
        
            new LoaderCallback<Beer[]>() {                                                  // optional callback
                @Override
                public void onLoadFinished(Beer[] data, Source source) {
                    MainFragment.this.onLoadFinished(data, source);
                }
            },
            new ViewBinder() {                                                              // data binding (optional too)
                @Override
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    return MainFragment.this.setViewValue(view, data, textRepresentation);
                }
            });
            
        // clear cache (optional)
        if (savedInstanceState == null) BaseResponse.clearTable(getActivity(),
                ((BaseResponseLoaderWrapper) mCoreLoad.getLoaders().iterator().next()).getTableName());
        
        startLoading(savedInstanceState, false);
    }

    private void startLoading(Bundle savedInstanceState, boolean byUserRequest) {
        if (byUserRequest) setBubblesState(true);

        mCoreLoad.setGoBackOnLoadingCanceled(!byUserRequest);

        rxSubscribe();      // optional

        mCoreLoad.startLoading(byUserRequest ? mCheckBoxForce.isChecked(): savedInstanceState != null,
            !byUserRequest, mCheckBoxMerge.isChecked(), false);
    }

    private void onLoadFinished(Beer[] data, Source source) {   // called from LoaderManager.LoaderCallbacks.onLoadFinished()
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
                
                return true;    // switch off default view binding

            case R.id.image:
                int pos = textRepresentation.indexOf("img_");
                view.setTag(textRepresentation.substring(pos + 4, pos + 6));

                ((ImageView) view).setImageURI(Uri.parse(textRepresentation));
                return true;
        }
        
        return false;           // default view binding will be applied
    }

    private void setPartToLoad(int counter) {
        getMainActivity().getJsonClient().setScenario("part" + counter);
    }

    private void setNetworkDelay() {
        getMainActivity().getJsonClient().setDelay(EMULATED_NETWORK_DELAY);
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    /////////// Rx handling (optional)

    private final RetrofitRx<Beer[]>    mRx                             = new RetrofitRx<>();
    private final Observable<BaseResponse<Response, Exception, Beer[]>>
                                        mRxObservable                   = mRx.createObservable();
    private       Subscription          mRxSubscription;

    private void rxSubscribe() {
        rxUnsubscribe();
        mRxSubscription = mRxObservable.subscribe(new Subscriber<BaseResponse<Response, Exception, Beer[]>>() {
            @Override public void onNext(BaseResponse<Response, Exception, Beer[]> beers) {
                Log.d("MainFragment", "Rx: onNext, data = " + Arrays.deepToString(beers.getResult()));
            }
            @Override public void onCompleted(           ) { Log.d("MainFragment", "Rx: onCompleted"); }
            @Override public void onError    (Throwable e) { Log.d("MainFragment", "Rx: onError",  e); }
        });
    }

    private void rxUnsubscribe() {
        if (mRxSubscription != null) mRxSubscription.unsubscribe();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // below is GUI stuff only

    private static final int            EMULATED_NETWORK_DELAY          = 3000;     // ms
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
                MainFragment.this, R.id.swipeContainer, mCheckBoxForce.isChecked(), mCheckBoxMerge.isChecked(), new Runnable() {
                    @Override
                    public void run() {
                        rxSubscribe();
                    }
                }));
    }

    // just a boilerplate code here
    private View initGui(LayoutInflater inflater, ViewGroup container) {
        View view           = inflater.inflate(R.layout.fragment_main, container, false);

        mGridView           = (AbsListView) view.findViewById(R.id.grid);

        mCheckBoxForce      = (CheckBox) view.findViewById(R.id.flag_force);
        mCheckBoxMerge      = (CheckBox) view.findViewById(R.id.flag_merge);

        view.findViewById(R.id.btn_load).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoading(null, true);
            }
        });

        mCheckBoxForce.setOnClickListener(mListener);
        mCheckBoxMerge.setOnClickListener(mListener);

        onAdjustMeasuredView(view.findViewById(R.id.container));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
            mSlideShow.init(view);

        return view;
    }

    private void initGui(final Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.keySet().contains(ARG_PART_COUNTER))
            mPartCounter = savedInstanceState.getInt(ARG_PART_COUNTER);
        setPartToLoad(mPartCounter);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
            mSlideShow.init(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            Bubbles.init(getActivity());
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(ARG_PART_COUNTER, mPartCounter);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
            mSlideShow.cleanUp();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            Bubbles.cleanUp();

        rxUnsubscribe();

        super.onDestroyView();
    }
    
    public void onSlideShow(final boolean isStarted) {
        setBubblesState(isStarted);
    }

    private void setBubblesState(final boolean cancel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            Bubbles.setState(cancel);
    }

    @SuppressWarnings("WeakerAccess")
    protected void onAdjustMeasuredView(final View view) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    mSlideRect = new Rect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                }
                catch (Exception e) {
                    Log.e("MainFragment", "onAdjustMeasuredView", e);
                }
                finally {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    else
                        removeListener();
                }
            }

            @SuppressWarnings("deprecation")
            private void removeListener() {
                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });
    }

    public Rect getSlideRect() {
        return mSlideRect;
    }
}
