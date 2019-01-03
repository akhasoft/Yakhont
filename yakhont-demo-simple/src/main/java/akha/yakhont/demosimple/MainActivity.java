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

import akha.yakhont.demosimple.model.Beer;
// import akha.yakhont.demosimple.model.BeerDefault;
import akha.yakhont.demosimple.retrofit.LocalJsonClient2;
import akha.yakhont.demosimple.retrofit.Retrofit2Api;

import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.retrofit.Retrofit2;
// import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;

@CallbacksInherited(LocationCallbacks.class)
public class MainActivity extends Activity implements LocationListener {

    private boolean mAdvertisementShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // uncomment if you're using Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      akha.yakhont.Core.setRxUncaughtExceptionBehavior(false);

        // uncomment to suppress location access confirmation dialog
//      LocationCallbacks.allowAccessToLocation(true);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((RecyclerView) findViewById(R.id.recycler))
                .setLayoutManager(new LinearLayoutManager(this));
/*
        ////////
        // normally it's enough - but here we have the local client; so see below...

        Retrofit2Loader.load("http://localhost/", Retrofit2Api.class,
                Retrofit2Api::getData, BR.beer);

        ////////
*/
        final Retrofit2<Retrofit2Api, Beer[]> retrofit2 = new Retrofit2<>();
        Retrofit2Loader.get("http://localhost/", Retrofit2Api.class,
                Retrofit2Api::getData, BR.beer,
                new LocalJsonClient2(retrofit2) /* .setEmulatedNetworkDelay(10) */ , retrofit2)
                /* .setGoBackOnCancelLoading(false) */ .load();
/*
        // recommended binding (based on Data Binding Library) - exactly the same as code above
        new Retrofit2CoreLoadBuilder<>(MainActivity.<Beer>createRetrofit())
                .setRequester(Retrofit2Api::getData)
// or           .setRequester(retrofit2Api -> retrofit2Api.getData("param"))

                // recommended way - but default binding also works (see below)
                .setDataBinding(BR.beer)

                .create()

                // uncomment to stay in Activity (and application) if user cancelled data loading
                // which means:
                // quite often, data loading goes during Activity initialization (and here too)
                // so, if user pressed Back during data loading, Yakhont shows confirmation dialog,
                //   and if 'yes' - cancels loading and calls Activity.onBackPressed()
                // for this simple demo application (only one Activity) it means -
                //   the application itself will be stopped
                // so uncomment if you don't want such Activity.onBackPressed() call
//              .setGoBackOnCancelLoading(false)

                .load();
// or
        // default binding (based on reflection)
        new Retrofit2CoreLoadBuilder<>(MainActivity.<BeerDefault>createRetrofit())
                .setRequester(Retrofit2Api::getData)
                .setListItem(R.layout.recycler_item_default)
                .create().load();
*/
    }
/*
    // every loader should have unique Retrofit2 object; don't share it with other loaders
    private static <T> Retrofit2<Retrofit2Api, T[]> createRetrofit() {
        final Retrofit2<Retrofit2Api, T[]> retrofit2 = new Retrofit2<>();

        // local JSON client, so URL doesn't matter
        // uncomment network delay emulation for the progress indicator
        return retrofit2.init(Retrofit2Api.class, "http://localhost/",
                new LocalJsonClient2(retrofit2));

        // for normal HTTP requests it's much simpler - just something like this:
//      return new Retrofit2<Retrofit2Api, T[]>().init(Retrofit2Api.class, "http://...");
    }
*/
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLocationChanged(Location location, Date date) {
        ((TextView) findViewById(R.id.location)).setText(LocationCallbacks.toDms(location, this));

        showAdvertisement();
    }

    @SuppressLint("InflateParams")
    private void showAdvertisement() {
        if (mAdvertisementShown) return;
        mAdvertisementShown = true;

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setView(LayoutInflater.from(this).inflate(R.layout.advertisement,
                null, false));
        toast.show();
    }
}
