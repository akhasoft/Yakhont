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

package akha.yakhont.demosimple;

import akha.yakhont.demosimple.retrofit.LocalJsonClient2;
import akha.yakhont.demosimple.retrofit.Retrofit2Api;

import akha.yakhont.Core;
import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.retrofit.Retrofit2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

@CallbacksInherited(LocationCallbacks.class)
public class MainActivity extends Activity implements LocationListener {

    private boolean mAdvertisementShown;

    private final Retrofit2<Retrofit2Api> mRetrofit2     = new Retrofit2<>();

    public Retrofit2<Retrofit2Api> getRetrofit() {
        return mRetrofit2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // uncomment if you're using Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      Core.setRxUncaughtExceptionBehavior(false);

        Core.init(getApplication());                        // initializes the Yakhont library

        // uncomment to suppress location access confirmation dialog
//      LocationCallbacks.allowAccessToLocation(true);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // local JSON client, so URL doesn't matter
        // uncomment network delay emulation for the progress dialog etc.
        mRetrofit2.init(Retrofit2Api.class, mRetrofit2.getDefaultBuilder("http://localhost/")
                .client(new LocalJsonClient2() /* .setEmulatedNetworkDelay(20) */ ));

        // for normal HTTP requests you can use something like this
//      sRetrofit2.init(Retrofit2Api.class, "http://...");
    }

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
        toast.setView(LayoutInflater.from(this).inflate(R.layout.advertisement, null, false));
        toast.show();
    }
}
