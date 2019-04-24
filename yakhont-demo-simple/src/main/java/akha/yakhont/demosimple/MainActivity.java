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

package akha.yakhont.demosimple;

import akha.yakhont.demosimple.model.Data;
import akha.yakhont.demosimple.retrofit.LocalOkHttpClient2;
import akha.yakhont.demosimple.retrofit.Retrofit2Api;

import akha.yakhont.Core;
import akha.yakhont.Core.Utils.CoreLoadHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader;

import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.paging.PagedList;
import androidx.paging.PositionalDataSource;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

@CallbacksInherited( /* value = */ LocationCallbacks.class /* , parameters = "permissions rationale demo" */ )
public class MainActivity extends AppCompatActivity implements LocationListener {

    private        CoreLoad<Throwable, List<Data>>      mLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // uncomment if you're using Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      akha.yakhont.Core.setRxUncaughtExceptionBehavior(false);
/*
        // customize default progress here
        akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefault.setProgressTextColor(...);
        akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefault.setConfirmTextColor(...);
        akha.yakhont.loader.BaseLiveData.LiveDataDialog.ProgressDefault.setConfirmDuration(...);
*/
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setLocation();

        ((RecyclerView) findViewById(R.id.recycler)).setLayoutManager(new LinearLayoutManager(this));
/*
        ////////
        // normally it should be enough - but here we have the local client, so see below...

        Retrofit2Loader.start("http://...", Retrofit2Api.class, Retrofit2Api::getData, BR.data,
                (Callable<SamplePositionalSource>) SamplePositionalSource::new, savedInstanceState);

        ////////
*/
        setDebugLogging(BuildConfig.DEBUG);         // optional

        if (savedInstanceState != null) {           // handling screen orientation changes
            mLoader = Retrofit2Loader.getExistingLoader();
            return;
        }

        Retrofit2<Retrofit2Api, List<Data>> retrofit2 = new Retrofit2<>();
        retrofit2.init(Retrofit2Api.class, "http://localhost/", new LocalOkHttpClient(retrofit2));

        mLoader = Retrofit2Loader.adjust(new Retrofit2CoreLoadBuilder<>(retrofit2)
                .setRequester(Retrofit2Api::getData)
// or           .setRequester(retrofit2Api -> retrofit2Api.getData(some parameter(s)))
                .setDataBinding(BR.data)
                .setPagingConfig(new PagedList.Config.Builder()
                        .setPageSize(LocalOkHttpClient.PAGE_SIZE)
                        .setEnablePlaceholders(false)
                        .build())
                .setPagingDataSourceProducer((Callable<DemoDataSource>) DemoDataSource::new)
                .create());
    }

    private class DemoDataSource extends PositionalDataSource<Data> {
        @Override
        public void loadInitial(@NonNull LoadInitialParams         params,
                                @NonNull LoadInitialCallback<Data> callback) {
            CoreLoadHelper.setPagingCallback(mLoader, (data, source) ->
                    callback.onResult(data, 0)).start(null);
        }

        @Override
        public void loadRange(@NonNull LoadRangeParams         params,
                              @NonNull LoadRangeCallback<Data> callback) {
            CoreLoadHelper.setPagingCallback(mLoader, (data, source) ->
                    callback.onResult(data)).start(null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // provides demo data for the endless paged adapter
    private static class LocalOkHttpClient extends LocalOkHttpClient2 {

        private static final int                PAGE_SIZE                       = 20;

        private int                             mItemCounter, mPageCounter;

        private LocalOkHttpClient(Retrofit2 retrofit2) {
            super(retrofit2);
            // just to demo the progress GUI - uncomment it if needed
//          setEmulatedNetworkDelay(7);
        }

        @Override
        protected String getJson() {
            StringBuilder builder = new StringBuilder("[");

            for (int i = mItemCounter; i < mItemCounter + PAGE_SIZE; i++)
                builder.append("{\"title\":\"").append("loaded page ").append(mPageCounter + 1)
                        .append(", item ").append(i + 1).append("\"},");

            mItemCounter += PAGE_SIZE;
            mPageCounter++;

            return builder.replace(builder.length() - 1, builder.length(), "]").toString();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void setDebugLogging(boolean debug) {
        if (!debug) return;

        Core.setFullLoggingInfo(true);

        // optional; on shaking device email with logs will be sent to the address below
        CoreLogger.registerShakeDataSender(this, "address@company.com");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static String                       sLocation;

    @Override
    public void onLocationChanged(Location location, Date date) {
        sLocation = LocationCallbacks.toDms(location, this);
        setLocation();
    }

    private void setLocation() {
        if (sLocation != null) ((TextView) findViewById(R.id.location)).setText(sLocation);
    }
}
