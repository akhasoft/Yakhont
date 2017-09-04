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

package akha.yakhont.technology;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.location.GoogleLocationClient;
import akha.yakhont.location.GoogleLocationClientNew;
import akha.yakhont.location.LocationCallbacks.LocationClient;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.annotation.StringRes;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Provider;

/**
 * The {@link <a href="http://google.github.io/dagger/">Dagger 2</a>} component. Usage example (see also
 * {@link akha.yakhont.Core#init(android.app.Application, Boolean, Dagger2) Theme example}):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.Core;
 * import akha.yakhont.fragment.dialog.ProgressDialogFragment;
 * import akha.yakhont.fragment.dialog.ProgressDialogFragment.ProgressLoaderDialogFragment;
 * import akha.yakhont.location.LocationCallbacks.LocationClient;
 * import akha.yakhont.technology.Dagger2;
 *
 * import dagger.BindsInstance;
 * import dagger.Component;
 * import dagger.Module;
 * import dagger.Provides;
 *
 * public class MyActivity extends Activity {
 *
 *     &#064;Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         Core.init(getApplication(), null, DaggerMyActivity_MyDagger
 *             .builder()
 *             .parameters(Dagger2.Parameters.create())
 *             .build()
 *         );
 *
 *         super.onCreate(savedInstanceState);
 *         ...
 *     }
 *
 *     // custom progress dialog example (with custom view R.layout.progress)
 *
 *     &#064;Component(modules = {MyLocationModule.class, MyUiModule.class})
 *     interface MyDagger extends Dagger2 {
 *
 *         &#064;Component.Builder
 *         interface Builder {
 *             &#064;BindsInstance
 *             Builder parameters(Dagger2.Parameters parameters);
 *             MyDagger build();
 *         }
 *     }
 *
 *     &#064;Module
 *     static class MyLocationModule extends Dagger2.LocationModule {
 *
 *         &#064;Provides
 *         LocationClient provideLocationClient(Dagger2.Parameters parameters) {
 *             return getLocationClient(getFlagLocation(parameters));
 *         }
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
 *     public static class MyProgress extends ProgressLoaderDialogFragment {
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
 *             return (MyProgress) ProgressDialogFragment.newInstance(
 *                 null, new MyProgress());
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
    String      UI_ALERT_PERMISSION             = "alert_permission";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_ALERT_PERMISSION_DENIED      = "alert_permission_denied";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_PROGRESS                     = "progress";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_LONG            = "toast_long";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_SHORT           = "toast_short";

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_LOCATION)          Provider<BaseDialog>     getAlertLocation();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PROGRESS)          Provider<BaseDialog>     getAlertProgress();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PERMISSION)        Provider<BaseDialog>     getAlertPermission();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PERMISSION_DENIED) Provider<BaseDialog>     getAlertPermissionDenied();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_PROGRESS)                Provider<BaseDialog>     getProgress();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_TOAST_LENGTH_LONG)       Provider<BaseDialog>     getToastLong();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    @Named(UI_TOAST_LENGTH_SHORT)      Provider<BaseDialog>     getToastShort();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    @Component(modules = {LocationModule.class, UiModule.class})
    interface DefaultComponent extends Dagger2 {
        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder parameters(Parameters parameters);
            DefaultComponent build();
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    Lazy<LocationClient> getLocationClient();

    /**
     * The parameters defined at run-time.
     */
    class Parameters {

        private static final int     VALUE_LOCATION   = 1;
        private static final int     VALUE_ALERT      = 2;
        private static final int     VALUE_TOAST      = 4;

        private        final int     mData;

        private static Parameters    sInstance;

        private Parameters(final int data) {
            if (sInstance != null) CoreLogger.logWarning("sInstance != null");
            mData     = data;
            sInstance = this;
        }

        /**
         * Returns the {@code Parameters} object in use.
         *
         * @return  The {@code Parameters} object
         */
        public static Parameters getInstance() {
            return sInstance;
        }

        private boolean get(final int value) {
            return (mData & value) == value;
        }

        /**
         * Creates new {@code Parameters} object.
         *
         * @param useGoogleLocationOldApi
         *        {@code true} for {@link com.google.android.gms.common.api.GoogleApiClient}-based Google Location API,
         *        {@code false} for {@link com.google.android.gms.location.FusedLocationProviderClient}-based one
         *
         * @param useSnackbarIsoAlert
         *        {@code true} for using {@link Snackbar} instead of dialog alert
         *
         * @param useSnackbarIsoToast
         *        {@code true} for using {@link Snackbar} instead of {@link Toast}
         *
         * @return  The {@code Parameters} object
         */
        public static Parameters create(final boolean useGoogleLocationOldApi,
                                        final boolean useSnackbarIsoAlert    ,
                                        final boolean useSnackbarIsoToast) {
            int flags = 0;

            if (useGoogleLocationOldApi) flags |= VALUE_LOCATION;
            if (useSnackbarIsoAlert    ) flags |= VALUE_ALERT   ;
            if (useSnackbarIsoToast    ) flags |= VALUE_TOAST   ;

            return new Parameters(flags);
        }

        /**
         * Creates new {@code Parameters} object with default settings.
         *
         * @return  The {@code Parameters} object
         */
        public static Parameters create() {
            return create(false, false, false);
        }
    }

    /**
     * The location client component.
     */
    @Module
    class LocationModule {

        /**
         * Initialises a newly created {@code LocationModule} object.
         */
        public LocationModule() {
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides
        LocationClient provideLocationClient(Parameters parameters) {
            return getLocationClient(getFlagLocation(parameters));
        }

        /**
         * Creates new instance of the location client.
         *
         * @param oldApi
         *        {@code true} for {@link com.google.android.gms.common.api.GoogleApiClient}-based Google Location API,
         *        {@code false} for {@link com.google.android.gms.location.FusedLocationProviderClient}-based one
         *
         * @return  The location client
         */
        @SuppressWarnings("WeakerAccess")
        protected LocationClient getLocationClient(final boolean oldApi) {
            return oldApi ? new GoogleLocationClient(): new GoogleLocationClientNew();
        }

        /**
         * Gets the {@code oldApi} flag (please refer to {@link #getLocationClient} method).
         *
         * @param parameters
         *        The {@code Parameters} object
         *
         * @return  The {@code oldApi} flag
         */
        @SuppressWarnings("WeakerAccess")
        protected boolean getFlagLocation(final Parameters parameters) {
            return parameters.get(Parameters.VALUE_LOCATION);
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

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_LOCATION)
        public BaseDialog provideLocationAlert(Parameters parameters) {
            return getAlert(getFlagAlert(parameters), akha.yakhont.R.string.yakhont_location_alert,
                    Utils.getRequestCode(RequestCodes.LOCATION_ALERT), false);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_PROGRESS)
        public BaseDialog provideProgressAlert(Parameters parameters) {
            return getAlert(false /* maybe subject to change */,
                    akha.yakhont.R.string.yakhont_loader_alert,
                    Utils.getRequestCode(RequestCodes.PROGRESS_ALERT), true);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_PERMISSION)
        public BaseDialog providePermissionAlert(Parameters parameters) {
            return getAlert(getFlagAlert(parameters), akha.yakhont.R.string.yakhont_permission_alert,
                    Utils.getRequestCode(RequestCodes.PERMISSIONS_RATIONALE_ALERT), false);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_PERMISSION_DENIED)
        public BaseDialog providePermissionDeniedAlert(Parameters parameters) {
            return getAlert(getFlagAlert(parameters), akha.yakhont.R.string.yakhont_permission_denied_alert,
                    Utils.getRequestCode(RequestCodes.PERMISSIONS_DENIED_ALERT), false);
        }

        /**
         * Creates new instance of the alert dialog.
         *
         * @param useSnackbarIsoAlert
         *        {@code true} for using {@link Snackbar} instead of dialog alert
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
        protected BaseDialog getAlert(final boolean useSnackbarIsoAlert, @StringRes final int resId,
                                      final int requestCode, final Boolean yesNo) {
            return useSnackbarIsoAlert ?
                    new BaseSnackbar(null, requestCode)
                            .setString(resId)
                            .setActionString(yesNo ?
                                    akha.yakhont.R.string.yakhont_alert_yes:
                                    akha.yakhont.R.string.yakhont_alert_ok):
                    SupportHelper.getAlert(resId, requestCode, yesNo);
        }

        /**
         * Gets the {@code useSnackbarIsoAlert} flag (please refer to {@link #getAlert} method).
         *
         * @param parameters
         *        The {@code Parameters} object
         *
         * @return  The {@code useSnackbarIsoAlert} flag
         */
        protected boolean getFlagAlert(final Parameters parameters) {
            return parameters.get(Parameters.VALUE_ALERT);
        }

        /**
         * Gets the {@code useSnackbarIsoToast} flag (please refer to {@link #getToast} method).
         *
         * @param parameters
         *        The {@code Parameters} object
         *
         * @return  The {@code useSnackbarIsoToast} flag
         */
        protected static boolean getFlagToast(final Parameters parameters) {
            return parameters.get(Parameters.VALUE_TOAST);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_PROGRESS)
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

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_TOAST_LENGTH_LONG)
        public BaseDialog provideLongToast(Parameters parameters) {
            return getToast(getFlagToast(parameters), true);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_TOAST_LENGTH_SHORT)
        public BaseDialog provideShortToast(Parameters parameters) {
            return getToast(getFlagToast(parameters), false);
        }

        /**
         * Creates new instance of a quick little text notification
         * (using {@link Toast} or {@link Snackbar}).
         *
         * @param useSnackbarIsoToast
         *        {@code true} for using {@link Snackbar} instead of {@link Toast}
         *
         * @param durationLong
         *        {@code true} to display the text notification for a long period of time, {@code false} otherwise
         *
         * @return  {@link Toast} or {@link Snackbar}
         */
        protected static BaseDialog getToast(final boolean useSnackbarIsoToast, final boolean durationLong) {
            return useSnackbarIsoToast ?
                    new BaseSnackbar(durationLong, null): new BaseToast(durationLong);
        }

        private static boolean getFlag() {
            return getFlagToast(Parameters.getInstance());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToast(@StringRes final int resId, final boolean durationLong) {
            show(getFlag(), null, resId, durationLong);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToast(final String text, final boolean durationLong) {
            show(getFlag(), text, Core.NOT_VALID_RES_ID, durationLong);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showSnackbar(final String text, final boolean durationLong) {
            show(true, text, Core.NOT_VALID_RES_ID, durationLong);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showSnackbar(@StringRes final int resId, final boolean durationLong) {
            show(true, null, resId, durationLong);
        }

        private static void show(final boolean useSnackbarIsoToast, String text,
                                 @StringRes final int resId, final boolean durationLong) {
            if (text == null && !validate(resId)) return;

            if (text == null) //noinspection ConstantConditions
                text = Utils.getApplication().getString(resId);

            if (useSnackbarIsoToast)
                new BaseSnackbar(durationLong, null).start(Utils.getCurrentActivity(), text, null);
            else
                new BaseToast(durationLong).start(Utils.getApplication(), text);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private static boolean validate(@StringRes final int resId) {
            try {
                //noinspection ConstantConditions
                return validate(Utils.getApplication().getString(resId));
            }
            catch (Exception exception) {
                CoreLogger.logError("validate failed", exception);
                return false;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean validate(final String text) {
            boolean     result = !TextUtils.isEmpty(text);
            if (result) result = text.trim().length() > 0;

            if (!result) CoreLogger.logError("text is empty");
            return result;
        }
    }
}

class BaseSnackbar implements BaseDialog {

    @IdRes
    private final int                       mListViewId;
    private       Integer                   mDuration;

    @StringRes
    private       int                       mStringId           = Core.NOT_VALID_RES_ID;
    private       String                    mString;

    @StringRes
    private       int                       mActionStringId     = Core.NOT_VALID_RES_ID;
    private       String                    mActionString;

    private       View.OnClickListener      mListener;
    private final Integer                   mRequestCode;

    private       WeakReference<Activity>   mActivity;
    private       Intent                    mIntent;

    private       WeakReference<Snackbar>   mSnackbar;
    private       boolean                   mShown;

    @SuppressWarnings("unused")
    BaseSnackbar(final Boolean durationLong, final Integer requestCode) {
        this(Core.NOT_VALID_VIEW_ID, durationLong, requestCode);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    BaseSnackbar(@SuppressWarnings("SameParameterValue") @IdRes final int listViewId,
                 final Boolean durationLong, final Integer requestCode) {

        mListViewId  = listViewId;
        mRequestCode = requestCode;

        if (durationLong != null)
            mDuration = durationLong ? Snackbar.LENGTH_LONG: Snackbar.LENGTH_SHORT;
    }

    public BaseSnackbar setString(@StringRes final int stringId) {
        mStringId = stringId;
        return this;
    }

    @SuppressWarnings("unused")
    public BaseSnackbar setString(final String string) {
        mString = string;
        return this;
    }

    public BaseSnackbar setActionString(@StringRes final int actionStringId) {
        mActionStringId = actionStringId;
        return this;
    }

    @SuppressWarnings("unused")
    public BaseSnackbar setActionString(final String actionString) {
        mActionString = actionString;
        return this;
    }

    @SuppressWarnings("unused")
    public BaseSnackbar setActionListener(final View.OnClickListener listener) {
        mListener = listener;
        return this;
    }

    private void onActivityResult(final int resultCode) {
        final Activity activity = mActivity.get();
        if (activity == null)
            CoreLogger.logError("activity == null");
        else
            Utils.onActivityResult(activity, mRequestCode, resultCode, mIntent);
    }

    @Override
    public boolean start(final Activity activity, final String text, final Intent data) {
        if (activity == null) {
            CoreLogger.logError("activity == null");
            return false;
        }
        try {
            mActivity   = new WeakReference<>(activity);
            mIntent     = data;

            return start(text);
        }
        catch (Exception e) {
            CoreLogger.log("start failed", e);
            return false;
        }
    }

    private boolean start(final String text) {
        final Activity activity = mActivity.get();
        if (activity == null) {
            CoreLogger.logError("activity == null");
            return false;
        }

        String                                      realText = text;
        if (realText == null)                       realText = mString;
        if (realText == null &&
                mStringId != Core.NOT_VALID_RES_ID) realText = activity.getString(mStringId);

        if (!Dagger2.UiModule.validate(realText)) return false;

        final View view = getView(activity);
        if (view == null) {
            CoreLogger.logError("View is null, can not show Snackbar: " + realText);
            return false;
        }

        if (mRequestCode != null) {
            if (mListener != null)
                CoreLogger.logError("listener is already defined, request code will be ignored");
            else
                mListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onActivityResult(Activity.RESULT_OK);
                    }
                };
        }

        if (mDuration == null) {
            if (mListener != null)
                mDuration = Snackbar.LENGTH_INDEFINITE;
            else {
                CoreLogger.logError("duration is not defined, set to LENGTH_LONG");
                mDuration = Snackbar.LENGTH_LONG;
            }
        }

        if (mSnackbar != null && mSnackbar.get() != null)
            CoreLogger.logError("Snackbar != null");

        @SuppressWarnings("ConstantConditions") final Snackbar snackbar = Snackbar.make(view, realText, mDuration);

        if (mListener != null) {
            if (mActionStringId != Core.NOT_VALID_RES_ID && mActionString != null)
                CoreLogger.logWarning("Both action string and action string ID were set; action string ID will be ignored");

            if (mActionString != null)
                snackbar.setAction(mActionString, mListener);
            else {
                if (mActionStringId == Core.NOT_VALID_RES_ID)
                    CoreLogger.logError("neither action string nor action string ID were not defined");
                else
                    snackbar.setAction(mActionStringId, mListener);
            }
        }

        snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(final Snackbar transientBottomBar, final int event) {
                super.onDismissed(transientBottomBar, event);

                mShown    = false;
                mSnackbar = null;

                switch (event) {
                    case BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION     :
                        // nothing to do
                        break;

                    case BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE:
                    case BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL     :
                    case BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE      :
                    case BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT    :
                        if (mRequestCode != null) onActivityResult(Activity.RESULT_CANCELED);
                        break;

                    default:
                        CoreLogger.logError("unknown BaseTransientBottomBar.BaseCallback event: " + event);
                        break;
                }
            }

            @Override
            public void onShown(final Snackbar transientBottomBar) {
                super.onShown(transientBottomBar);

                mShown = true;
            }
        });

        mSnackbar = new WeakReference<>(snackbar);

        snackbar.show();

        return true;
    }

    private View getView(final Activity activity) {
        View view = ViewHelper.getView(activity, mListViewId);

        if (view != null) {
            final View viewTmp = ViewHelper.findView(view, new ViewHelper.ViewVisitor() {
                @Override
                public boolean handle(final View view) {
                    return !(view instanceof ViewGroup);
                }
            });
            if (viewTmp != null) view = viewTmp;
        }
        if (view == null)
            CoreLogger.logError("view == null");

        return view;
    }

    @Override
    public boolean stop() {
        if (!mShown) return true;

        if (mSnackbar == null || mSnackbar.get() == null) {
            CoreLogger.logError("Snackbar == null");
            return false;
        }

        mSnackbar.get().dismiss();
        mSnackbar = null;

        return true;
    }
}

class BaseToast implements BaseDialog {

    private final boolean       mDurationLong;

    BaseToast(final boolean durationLong) {
        mDurationLong = durationLong;
    }

    public boolean start(final Context context, final String text) {
        try {
            if (context == null)
                CoreLogger.logError("context == null");
            else if (Dagger2.UiModule.validate(text)) {
                Toast.makeText(context, text,
                        mDurationLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        catch (Exception e) {
            CoreLogger.log("start failed", e);
        }
        return false;
    }

    @Override
    public boolean start(final Activity activity, final String text, final Intent data) {
        return start(activity, text);
    }

    @Override
    public boolean stop() {     // not used
        return true;
    }
}
