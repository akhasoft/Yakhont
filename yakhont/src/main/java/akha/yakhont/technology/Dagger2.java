/*
 * Copyright (C) 2016 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.technology;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.location.GoogleLocationClient;
import akha.yakhont.location.LocationCallbacks.LocationClient;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.StringRes;
import android.widget.Toast;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Provider;

/**
 * The {@link <a href="http://google.github.io/dagger/">Dagger 2</a>} component. Usage example (see also
 * {@link akha.yakhont.Core#run(android.app.Application, Boolean, Dagger2) Theme example} and
 * {@link UiModule#getToast(boolean) Toast example}):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.Core;
 * import akha.yakhont.fragment.dialog.ProgressDialogFragment;
 *
 * import dagger.Component;
 * import dagger.Module;
 *
 * public class MyActivity extends Activity {
 *
 *     &#064;Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         Core.run(getApplication(), BuildConfig.DEBUG, DaggerMyActivity_MyDagger.create());
 *
 *         super.onCreate(savedInstanceState);
 *         ...
 *     }
 *
 *     // custom progress dialog example (with custom view R.layout.progress)
 *
 *     &#064;Component(modules = {Dagger2.LocationModule.class, MyUiModule.class})
 *     interface MyDagger extends Dagger2 {
 *     }
 *
 *     &#064;Module
 *     static class MyUiModule extends Dagger2.UiModule {
 *
 *         &#064;Override
 *         protected Core.BaseDialog getProgress() {
 *             return MyProgress.newInstance();
 *         }
 *     }
 *
 *     public static class MyProgress extends ProgressDialogFragment.ProgressLoaderDialogFragment {
 *
 *         &#064;Override
 *         public Dialog onCreateDialog(Bundle savedInstanceState) {
 *             Activity activity = getActivity();
 *             AlertDialog.Builder builder = new AlertDialog.Builder(activity);
 *
 *             View view = LayoutInflater.from(activity).inflate(R.layout.progress, null);
 *             ((TextView) view.findViewById(R.id.progress_message)).setText(getMessage());
 *
 *             return builder.setView(view).create();
 *         }
 *
 *         public static MyProgress newInstance() {
 *             return (MyProgress) ProgressDialogFragment.newInstance(null, new MyProgress());
 *         }
 *     }
 * }
 * </pre>
 *
 * @author akha
 */
public interface Dagger2 {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_ALERT_LOCATION               = "alert_location";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_ALERT_PROGRESS               = "alert_progress";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_PROGRESS                     = "progress";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_LONG            = "toast_long";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_SHORT           = "toast_short";

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    @Component(modules = {LocationModule.class, UiModule.class})
    interface DefaultComponent extends Dagger2 {
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    LocationClient getLocationClient();

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_LOCATION)       Provider<BaseDialog> getAlertLocation();   
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PROGRESS)       Provider<BaseDialog> getAlertProgress();   
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_PROGRESS)             Provider<BaseDialog> getProgress();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_TOAST_LENGTH_LONG)    Provider<BaseDialog> getToastLong(); 
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    @Named(UI_TOAST_LENGTH_SHORT)   Provider<BaseDialog> getToastShort();

    /**
     * The location client component.
     */
    @Module
    @SuppressWarnings("unused")
    class LocationModule {

        /**
         * Initialises a newly created {@code LocationModule} object.
         */
        public LocationModule() {
        }

        /** @exclude */
        @Provides
        @SuppressWarnings({"JavaDoc", "unused"})
        public LocationClient provideLocationClient() {
            return getLocationClient();
        }

        /**
         * Creates new instance of the location client.
         *
         * @return  The location client
         */
        protected LocationClient getLocationClient() {
            return new GoogleLocationClient().setRequestingLocationUpdates(true, false);
        }
    }

    /**
     * The user interface component.
     */
    @Module
    @SuppressWarnings("unused")
    class UiModule {

        /**
         * Initialises a newly created {@code UiModule} object.
         */
        public UiModule() {
        }

        /** @exclude */
        @Provides @Named(UI_ALERT_LOCATION)
        @SuppressWarnings({"JavaDoc", "unused"})
        public BaseDialog provideLocationAlert() {
            return getAlert(akha.yakhont.R.string.yakhont_location_alert, Utils.getRequestCode(RequestCodes.LOCATION_ALERT), false);
        }

        /** @exclude */
        @Provides @Named(UI_ALERT_PROGRESS)
        @SuppressWarnings({"JavaDoc", "unused"})
        public BaseDialog provideProgressAlert() {
            return getAlert(akha.yakhont.R.string.yakhont_loader_alert,   Utils.getRequestCode(RequestCodes.PROGRESS_ALERT),  true);
        }

        /**
         * Creates new instance of the alert dialog.
         *
         * @param resId
         *        The resource ID of the dialog message's text
         *
         * @param requestCode
         *        The integer request code to pass to the {@link Activity#onActivityResult Activity.onActivityResult()}
         *
         * @param yesNo
         *        {@code true} for YES / NO buttons, {@code false} for OK / CANCEL ones
         *
         * @return  The alert dialog
         */
        protected BaseDialog getAlert(@StringRes final int resId, final int requestCode, final Boolean yesNo) {
            return SupportHelper.getAlert(resId, requestCode, yesNo);
        }

        /** @exclude */
        @Provides @Named(UI_PROGRESS)
        @SuppressWarnings({"JavaDoc", "unused"})
        public BaseDialog provideProgress() {
            return getProgress();
        }

        /**
         * Creates new instance of the progress dialog.
         *
         * @return  The progress dialog
         */
        protected BaseDialog getProgress() {
            return SupportHelper.getProgress();
        }

        /** @exclude */
        @Provides @Named(UI_TOAST_LENGTH_LONG)
        @SuppressWarnings({"JavaDoc", "unused"})
        public BaseDialog provideLongToast() {
            return getToast(true);
        }

        /** @exclude */
        @Provides @Named(UI_TOAST_LENGTH_SHORT)
        @SuppressWarnings({"JavaDoc", "unused"})
        public BaseDialog provideShortToast() {
            return getToast(false);
        }

        /**
         * Creates new instance of a quick little text notification (using {@link Toast}). The
         * {@link <a href="http://developer.android.com/reference/android/support/design/widget/Snackbar.html">Snackbar</a>} example:
         *
         * <pre style="background-color: silver; border: thin solid black;">
         * import akha.yakhont.Core;
         *
         * import android.support.design.widget.Snackbar;
         *
         * import dagger.Component;
         * import dagger.Module;
         *
         * public class MyActivity extends Activity {
         *
         *     &#064;Override
         *     protected void onCreate(Bundle savedInstanceState) {
         *         Core.run(getApplication(), BuildConfig.DEBUG, DaggerMyActivity_MyDagger.create());
         *
         *         super.onCreate(savedInstanceState);
         *         ...
         *     }
         *
         *     &#064;Component(modules = {Dagger2.LocationModule.class, MyUiModule.class})
         *     interface MyDagger extends Dagger2 {
         *     }
         *
         *     &#064;Module
         *     static class MyUiModule extends Dagger2.UiModule {
         *
         *         &#064;Override
         *         protected Core.BaseDialog getToast(boolean durationLong) {
         *             return new MySnackbar(durationLong);
         *         }
         *     }
         *
         *     public static class MySnackbar implements Core.BaseDialog {
         *
         *         private final boolean mDurationLong;
         *
         *         public MySnackbar(boolean durationLong) {
         *             mDurationLong = durationLong;
         *         }
         *
         *         &#064;Override
         *         public boolean start(Context context, String text) {
         *             Snackbar.make(getMyView((Activity) context), text,
         *                     mDurationLong ? Snackbar.LENGTH_LONG: Snackbar.LENGTH_SHORT).show();
         *             return true;
         *         }
         *
         *         private View getMyView(Activity activity) {
         *             // your code here - something like "return activity.findViewById(...);"
         *         }
         *
         *         &#064;Override
         *         public boolean stop() {
         *             return true;
         *         }
         *     }
         * }
         * </pre>
         *
         * @param durationLong
         *        {@code true} to display the text notification for a long period of time, {@code false} otherwise
         *
         * @return  The text notification
         */
        protected BaseDialog getToast(final boolean durationLong) {
            return new BaseToast(durationLong);
        }
    }
}

class BaseToast implements BaseDialog {

    private final boolean       mDurationLong;

    BaseToast(final boolean durationLong) {
        mDurationLong = durationLong;
    }

    @Override
    public boolean start(final Context context, final String text) {
        try {
            if (context != null) {
                Toast.makeText(context, text, mDurationLong ? Toast.LENGTH_LONG: Toast.LENGTH_SHORT).show();
                return true;
            }
            CoreLogger.logError("context == null");
        }
        catch (Exception e) {
            CoreLogger.log("failed", e);
        }
        return false;
    }

    @Override
    public boolean stop() {     // not used
        return true;
    }
}
