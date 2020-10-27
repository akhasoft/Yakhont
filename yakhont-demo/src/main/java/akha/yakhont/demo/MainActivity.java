/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationClient;
import akha.yakhont.location.LocationCallbacks.LocationListener;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.Dagger2.CallbacksValidationModule;
import akha.yakhont.technology.Dagger2.LocationModule;
import akha.yakhont.technology.Dagger2.Parameters;
import akha.yakhont.technology.Dagger2.UiModule;
import akha.yakhont.technology.rx.BaseRx.LocationRx;
import akha.yakhont.technology.rx.BaseRx.RxVersions;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;

import com.google.android.material.snackbar.Snackbar;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;

@CallbacksInherited( /* value = */ LocationCallbacks.class /* , properties = R.string.permissions_rationale_demo */ )
public class MainActivity extends AppCompatActivity implements LocationListener /* optional */ {

    // well, it's also possible to use all that stuff (versions 1 - 3) simultaneously
    // but I wouldn't like to mess the demo with such kind of extravaganza
    private static final boolean            USE_RETROFIT_2                  = true;
    private static final RxVersions         USE_RX_VERSION                  = RxVersions.VERSION_3;

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

        // overrides the default configuration from weaver.config (enables screen orientation setting)
        Core.config(null, true, true, null);

        boolean debug = BuildConfig.DEBUG;
        setDebugLogging(debug);             // optional

        if (savedInstanceState == null)
            Core.init(getApplication(), debug, DaggerMainActivity_DemoDagger    // deep customization
                    .builder()
                    .parameters(Parameters.create(USE_GOOGLE_LOCATION_OLD_API, USE_SNACKBAR_ISO_TOAST))
                    .build());

        setTheme(R.style.AppThemeCompat);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) Utils.showToast(R.string.demo_description, 7);

        initLocationRx();   // optional
/*
        // set location client parameters here (if necessary)
        ((akha.yakhont.location.BaseGoogleLocationClient) mLocationCallbacks.getLocationClient())
                .setLocationUpdatesParameters(...);
*/
        // noinspection Convert2Lambda
        findViewById(R.id.fab_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.showSnackbar(LocationCallbacks.toDms(getLocation(), MainActivity.this),
                        Snackbar.LENGTH_LONG);
            }
        });

        // noinspection Convert2Lambda
        findViewById(R.id.fab_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Utils.showToast(R.string.info, Toast.LENGTH_LONG);
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
    public RxVersions getRxVersion() {
        return USE_RX_VERSION;
    }

    private void initLocationRx() {
        mLocationCallbacks = LocationCallbacks.getLocationCallbacks(this);
        if (mLocationCallbacks == null) return;

        // unsubscribe handled by Yakhont
        mRx = new LocationRx(getRxVersion(), this).subscribe(new SubscriberRx<Location>() {
            @Override
            public void onNext(Location location) {
                CoreLogger.log("LocationRx: " + location);
            }

            @Override
            public void onError(Throwable throwable) {
                CoreLogger.log(throwable);
            }
        });

        // unregister handled by Yakhont
        mLocationCallbacks.register(this, mRx);
    }

    private Location getLocation() {
        return mRx.getResult();
    }

    /////////// end of Rx handling

    @Override
    public void onLocationChanged(Location location, Date date) {
        CoreLogger.log("onLocationChanged: " + location);
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

    public void setSlideShow(SlideShow slideShow) {
        mSlideShow = slideShow;
    }

    private void setDebugLogging(boolean debug) {
        if (debug) Core.setFullLoggingInfo(true);

        // optional; on shaking device (or make Z-gesture) email with logs will be sent to the address below
        CoreLogger.registerDataSender(this, "address@company.com");
/*
            // or something like this:
            CoreLogger.VideoRecorder.setVideoFrameRate(...);    // optional, of course
            CoreLogger.VideoRecorder.setAudioFormat   (...);    // optional too

            CoreLogger.setShakeParameters             (...);    // option for shake threshold and delay
            CoreLogger.setGestureLibrary              (...);    // IMHO exotic option (but powerful anyway)
*/
//          CoreLogger.registerDataSender(this, null, "logcat -d", true, true, null, "subj", "address@company.com");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // deep Yakhont customization example

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

    // customize Yakhont callbacks validation here
    // it's optional - use CallbacksValidationModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoCallbacksValidationModule extends CallbacksValidationModule {
        @SuppressWarnings("EmptyMethod")
        @Override
        protected Validator getCallbacksValidator() {
            return super.getCallbacksValidator();
        }
    }

    // customize Yakhont location client here
    // it's optional - use LocationModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoLocationModule extends LocationModule {
        @SuppressWarnings("EmptyMethod")
        @Override
        protected LocationClient getLocationClient(boolean oldApi) {
            return super.getLocationClient(oldApi);
        }
    }

    // customize Yakhont GUI here
    // it's optional - use UiModule.class for default implementation (see @Component above)
    @SuppressWarnings("WeakerAccess")
    @Module
    static class DemoUiModule extends UiModule {
        @SuppressWarnings("EmptyMethod")
        @Override
        protected BaseDialog getPermissionAlert(Integer requestCode, Integer duration) {
            return super.getPermissionAlert(requestCode, duration);
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        protected BaseDialog getToast(boolean useSnackbarIsoToast, Integer duration) {
            return super.getToast(useSnackbarIsoToast, duration);
        }
    }
}
