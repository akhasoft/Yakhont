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

import akha.yakhont.demo.gui.Utils;
import akha.yakhont.demo.retrofit.LocalJsonClient;
import akha.yakhont.demo.retrofit.LocalJsonClient2;
import akha.yakhont.demo.retrofit.Retrofit2Api;
import akha.yakhont.demo.retrofit.RetrofitApi;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.callback.annotation.CallbacksInherited;
// import akha.yakhont.location.BaseGoogleLocationClient;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationClient;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.retrofit.Retrofit;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.rx.BaseRx.LocationRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import akha.yakhont.support.fragment.dialog.ProgressDialogFragment;

// for using non-support version of library (android.app.Fragment etc.):
// comment out akha.yakhont.support.fragment.* import above and uncomment one below

// also, don't forget to change in build.gradle 'yakhont-support' to 'yakhont' (or 'yakhont-full')

// import akha.yakhont.fragment.dialog.ProgressDialogFragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

import java.util.Date;

@CallbacksInherited(LocationCallbacks.class)
public class MainActivity extends /* Activity */ android.support.v7.app.AppCompatActivity
        implements LocationListener /* optional */ {

    // well, it's also possible to use all that stuff (versions 1 and 2) simultaneously
    // but I wouldn't like to mess the demo with such kind of extravaganza
    private static final boolean            USE_RETROFIT_2                  = true;
    private static final boolean            USE_RX_JAVA_2                   = true;

    private static final boolean            USE_GOOGLE_LOCATION_OLD_API     = false;

    private       LocalJsonClient           mJsonClient;
    private       LocalJsonClient2          mJsonClient2;

    private final Retrofit <RetrofitApi>    mRetrofit                       = new Retrofit <>();
    private final Retrofit2<Retrofit2Api>   mRetrofit2                      = new Retrofit2<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Core.init(getApplication(), BuildConfig.DEBUG, DaggerMainActivity_DemoDagger.create());

        LocationCallbacks.allowAccessToLocation(true);      // suppress confirmation dialog

        // optional; on shaking device will send email with logs to the address below
        if (BuildConfig.DEBUG) CoreLogger.registerShakeDataSender(this, "yourname@yourcompany.com");

        //noinspection ConstantConditions
        setTheme(SupportHelper.isSupportMode(this) ? R.style.AppThemeCompat: R.style.AppThemeCompat_Special);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (isRetrofit2())
            mJsonClient2 = new LocalJsonClient2(this);
        else
            mJsonClient  = new LocalJsonClient (this);

        // local JSON client, so URL doesn't matter
        String url = "http://localhost/";
        if (isRetrofit2())
            mRetrofit2.init(Retrofit2Api.class, mRetrofit2.getDefaultBuilder(url).   client(mJsonClient2));
        else
            mRetrofit .init(RetrofitApi.class,  mRetrofit .getDefaultBuilder(url).setClient(mJsonClient));

        initLocationRx();   // optional

        // set location client parameters here (if necessary)
//      ((BaseGoogleLocationClient) mLocationCallbacks.getLocationClient()).setLocationUpdatesParameters(...);

        //noinspection ConstantConditions
        findViewById(R.id.fab_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, LocationCallbacks.toDms(getLocation(), MainActivity.this),
                    Snackbar.LENGTH_LONG).show();
            }
        });

        //noinspection ConstantConditions
        findViewById(R.id.fab_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, getString(R.string.info), Toast.LENGTH_LONG).show();
            }
        });
    }

    @SuppressWarnings("SameReturnValue")
    public boolean isRetrofit2() {
        return USE_RETROFIT_2;
    }

    /////////// Rx handling (optional)

    @SuppressWarnings("FieldCanBeLocal")
    private       LocationCallbacks         mLocationCallbacks;
    private       LocationRx                mRx;

    @SuppressWarnings("SameReturnValue")
    public boolean isRxJava2() {
        return USE_RX_JAVA_2;
    }

    private void initLocationRx() {
        mLocationCallbacks = LocationCallbacks.getLocationCallbacks(this);
        if (mLocationCallbacks == null) return;

        mRx = new LocationRx(isRxJava2(), this).subscribe(new SubscriberRx<Location>() {
            @Override
            public void onNext(final Location location) {
                Log.w("MainActivity", "LocationRx: " + location);
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("MainActivity", "LocationRx: " + throwable);
            }
        });

        // avoids terminating the application by calls to the Rx uncaught exception handler
        // actually it's the final application only that should set (or not) such kind of handlers:
        // it's not advised for intermediate libraries to change the global handlers behavior
        mRx.setErrorHandlerJustLog();

        // unsubscribe and unregister goes automatically
        mLocationCallbacks.register(this, mRx);
    }

    private Location getLocation() {
        return mRx.getResult();
    }

    /////////// end of Rx handling

    @Override
    public void onLocationChanged(Location location, Date date) {
        Log.w("MainActivity", "onLocationChanged: " + location);
    }

    public Retrofit <RetrofitApi>  getRetrofit() {
        return mRetrofit;
    }

    public Retrofit2<Retrofit2Api> getRetrofit2() {
        return mRetrofit2;
    }

    public void setScenario(String scenario) {
        if (isRetrofit2())
            mJsonClient2.getLocalJsonClientHelper().setScenario(scenario);
        else
            mJsonClient .getLocalJsonClientHelper().setScenario(scenario);
    }

    public void setNetworkDelay(@SuppressWarnings("SameParameterValue") int delay) {
        if (isRetrofit2())
            mJsonClient2.getLocalJsonClientHelper().setEmulatedNetworkDelay(delay);
        else
            mJsonClient .getLocalJsonClientHelper().setEmulatedNetworkDelay(delay);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    // custom progress dialog (with background image) example (using custom view R.layout.progress)

    @Component(modules = {DemoLocationModule.class, DemoUiModule.class})
    interface DemoDagger extends Dagger2 {
    }

    @Module
    static class DemoLocationModule extends Dagger2.LocationModule {
        @Provides
        LocationClient provideLocationClient() {
            return getLocationClient(USE_GOOGLE_LOCATION_OLD_API);
        }
    }

    @Module
    static class DemoUiModule extends Dagger2.UiModule {
        @Override
        protected BaseDialog getProgress() {
            return DemoProgress.newInstance();
        }
    }

    public static class DemoProgress extends ProgressDialogFragment.ProgressLoaderDialogFragment {

        private static BitmapDrawable   sBackground;

        @NonNull
        @Override
        @SuppressWarnings({"unused", "UnusedParameters"})
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Activity activity = getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            @SuppressLint("InflateParams")
            View view = LayoutInflater.from(activity).inflate(R.layout.progress, null);
            ((TextView) view.findViewById(R.id.progress_message)).setText(getMessage(activity));

            if (sBackground == null) {
                DisplayMetrics dm       = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
                
                Resources resources     = activity.getResources();
                sBackground             = new BitmapDrawable(resources, Utils.decodeBitmap(
                    resources, R.drawable.img_progress, dm.heightPixels, dm.widthPixels));
            }
            ((ImageView) view.findViewById(R.id.progress_background)).setImageDrawable(sBackground);

            return builder.setView(view).create();
        }

        @Override
        @SuppressWarnings("unused")
        public void onCancel(DialogInterface dialog) {  // makes sense only if confirmation dialog switched off (see below)
            super.onCancel(dialog);
            Toast.makeText(getActivity(), getString(R.string.yakhont_loader_cancelled), Toast.LENGTH_SHORT).show();
        }

        public static DemoProgress newInstance() {
            return (DemoProgress) ProgressDialogFragment.newInstance(null, new DemoProgress()
                    .setConfirmation(false) /* for demo only */ );
        }
    }
}
