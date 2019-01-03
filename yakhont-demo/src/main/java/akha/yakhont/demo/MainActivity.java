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

import akha.yakhont.demo.gui.SlideShow;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.callback.BaseCallbacks.Validator;
import akha.yakhont.callback.annotation.CallbacksInherited;
// import akha.yakhont.location.BaseGoogleLocationClient;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationClient;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.Dagger2.CallbacksValidationModule;
import akha.yakhont.technology.Dagger2.LocationModule;
import akha.yakhont.technology.Dagger2.Parameters;
import akha.yakhont.technology.Dagger2.UiModule;
import akha.yakhont.technology.rx.BaseRx.LocationRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;

import java.util.Date;

@CallbacksInherited(LocationCallbacks.class)
public class MainActivity extends AppCompatActivity implements LocationListener /* optional */ {

    // well, it's also possible to use all that stuff (versions 1 and 2) simultaneously
    // but I wouldn't like to mess the demo with such kind of extravaganza
    private static final boolean            USE_RETROFIT_2                  = true;
    private static final boolean            USE_RX_JAVA_2                   = true;

    private static final boolean            USE_GOOGLE_LOCATION_OLD_API     = false;
    private static final boolean            USE_SNACKBAR_ISO_TOAST          = false;

    private              SlideShow          mSlideShow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // avoids terminating the application by calls to the Rx uncaught exception handler
        // actually it's the final application only that should set (or not) such kind of handlers:
        // it's not advised for intermediate libraries to change the global handlers behavior
        //
        // For more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        Core.setRxUncaughtExceptionBehavior(false /* not terminate */);

        Core.init(getApplication(), BuildConfig.DEBUG, DaggerMainActivity_DemoDagger // deep customization (see below)
                .builder()
                .parameters(Parameters.create(USE_GOOGLE_LOCATION_OLD_API, USE_SNACKBAR_ISO_TOAST))
                .build() );

        LocationCallbacks.allowAccessToLocation(true);      // suppress confirmation dialog

        // optional; on shaking device email with logs will be sent to the address below
        if (BuildConfig.DEBUG) CoreLogger.registerShakeDataSender(this, "yourname@yourcompany.com");

        //noinspection ConstantConditions
        setTheme(R.style.AppThemeCompat);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initLocationRx();   // optional

        // set location client parameters here (if necessary)
//      ((BaseGoogleLocationClient) mLocationCallbacks.getLocationClient()).setLocationUpdatesParameters(...);

        //noinspection ConstantConditions,Convert2Lambda
        findViewById(R.id.fab_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.showSnackbar(LocationCallbacks.toDms(getLocation(), MainActivity.this),
                        Utils.SHOW_DURATION_LONG);
            }
        });

        //noinspection ConstantConditions,Convert2Lambda
        findViewById(R.id.fab_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.showToast(R.string.info, Utils.SHOW_DURATION_LONG);
            }
        });
    }

    @SuppressWarnings("SameReturnValue")
    public boolean isRetrofit2() {
        return USE_RETROFIT_2;
    }

    /////////// Rx handling (optional)

    @SuppressWarnings("FieldCanBeLocal")
    private              LocationCallbacks  mLocationCallbacks;
    private              LocationRx         mRx;

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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        if (mSlideShow != null)
            mSlideShow.cleanUpSlideShow();
        else
            super.onBackPressed();
    }

    public void setSlideShow(final SlideShow slideShow) {
        mSlideShow = slideShow;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // deep customization example

    // default implementation - customize only modules you need
//  @Component(modules = {CallbacksValidationModule.class, LocationModule.class, UiModule.class})

    @Component(modules = {DemoCallbacksValidationModule.class, DemoLocationModule.class, DemoUiModule.class})
    interface DemoDagger extends Dagger2 {
        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder parameters(Parameters parameters);
            DemoDagger build();
        }
    }

    // customize callbacks validation here
    // it's optional - use CallbacksValidationModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoCallbacksValidationModule extends CallbacksValidationModule {
        @Override
        protected Validator getCallbacksValidator() {
            return super.getCallbacksValidator();
        }
    }

    // customize location client here
    // it's optional - use LocationModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoLocationModule extends LocationModule {
        @Override
        protected LocationClient getLocationClient(final boolean oldApi) {
            return super.getLocationClient(oldApi);
        }
    }

    // customize Yakhont GUI here
    // it's optional - use UiModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoUiModule extends UiModule {
        @Override
        protected BaseDialog getAlert(@StringRes int resId, int requestCode, Boolean yesNo) {
            return super.getAlert(resId, requestCode, yesNo);
        }

        @Override
        protected BaseDialog getToast(boolean useSnackbarIsoToast, boolean durationLong) {
            return super.getToast(useSnackbarIsoToast, durationLong);
        }
    }
}
