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
import akha.yakhont.demo.retrofit.RetrofitApi;

import akha.yakhont.Core;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.rx.BaseRx.LocationRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;
import akha.yakhont.technology.retrofit.Retrofit;

import akha.yakhont.support.fragment.dialog.ProgressDialogFragment;

// for using non-support version of library (android.app.Fragment etc.):
// comment out akha.yakhont.support.fragment.* import above and uncomment one below

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

import java.util.Date;

@CallbacksInherited(LocationCallbacks.class)
public class MainActivity extends /* Activity */ android.support.v7.app.AppCompatActivity
        implements LocationCallbacks.LocationListener /* optional */ {

    private       LocalJsonClient           mJsonClient;

    private final Retrofit<RetrofitApi>     mRetrofit                       = new Retrofit<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Core.run(getApplication(), BuildConfig.DEBUG, DaggerMainActivity_DemoDagger.create());

        LocationCallbacks.allowAccessToLocation(true);      // suppress confirmation dialog

        // optional; on shaking device will send email with logs to the address below
        if (BuildConfig.DEBUG) CoreLogger.registerShakeDataSender(this, "yourname@yourcompany.com");

        //noinspection ConstantConditions
        setTheme(SupportHelper.isSupportMode(this) ? R.style.AppThemeCompat: R.style.AppThemeCompat_Hack);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mJsonClient = new LocalJsonClient(this);

        // local JSON client, so URL doesn't matter
        mRetrofit.init(RetrofitApi.class, mRetrofit.getDefaultBuilder("http://localhost/").setClient(mJsonClient));

        initLocationRx();   // optional

        //noinspection ConstantConditions
        findViewById(R.id.fab_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Location location = getLocation();
                Snackbar.make(view, getString(R.string.location_msg,
                    location == null ? getString(R.string.location_msg_na): LocationCallbacks.toDms(location)), 
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

    /////////// Rx handling (optional)

    private       LocationCallbacks         mLocationCallbacks;
    private       LocationRx                mRx;

    @SuppressWarnings("SameReturnValue")
    public boolean isRx2() {
        return false;
    }

    private void initLocationRx() {
        mLocationCallbacks = LocationCallbacks.getLocationCallbacks(this);
        if (mLocationCallbacks == null) return;

        mRx = new LocationRx(isRx2(), this).subscribe(new SubscriberRx<Location>() {
            @Override
            public void onNext(final Location location) {
                Log.w("MainActivity", "LocationRx: " + location);
            }
        });

        // avoids terminating the application by calls to the Rx uncaught exception handler
        // actually it's the final application only that should set (or not) such kind of handlers:
        // it's not advised for intermediate libraries to change the global handlers behavior
        mRx.setErrorHandlerJustLog();

        mLocationCallbacks.register(mRx);
    }

    @Override
    protected void onDestroy() {
        if (mRx                != null) mRx.cleanup();
        if (mLocationCallbacks != null) mLocationCallbacks.unregister(mRx);

        super.onDestroy();
    }

    private Location getLocation() {
        return mRx.getResult();
    }

    /////////// end of Rx handling

    @Override
    public void onLocationChanged(Location location, Date date) {
        Log.d("MainActivity", "onLocationChanged: " + location);
    }

    public LocalJsonClient getJsonClient() {
        return mJsonClient;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    // custom progress dialog (with background image) example (using custom view R.layout.progress)

    @Component(modules = {Dagger2.LocationModule.class, DemoUiModule.class})
    @SuppressWarnings("unused")
    interface DemoDagger extends Dagger2 {
    }

    @Module
    @SuppressWarnings("unused")
    static class DemoUiModule extends Dagger2.UiModule {
        @Override
        protected Core.BaseDialog getProgress() {
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
