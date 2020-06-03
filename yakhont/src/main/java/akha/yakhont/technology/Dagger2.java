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

package akha.yakhont.technology;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.R;
import akha.yakhont.callback.BaseCallbacks;
import akha.yakhont.callback.BaseCallbacks.Validator;
import akha.yakhont.location.GoogleLocationClient;
import akha.yakhont.location.GoogleLocationClientNew;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.location.LocationCallbacks.LocationClient;
import akha.yakhont.technology.Dagger2.UiModule;
import akha.yakhont.technology.Dagger2.UiModule.ViewHandlerSnackbar;
import akha.yakhont.technology.Dagger2.UiModule.ViewModifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import javax.inject.Named;
import javax.inject.Provider;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.Snackbar.Callback;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

/**
 * The {@link <a href="https://dagger.dev/">Dagger 2</a>} component. Usage example:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.Core;
 * import akha.yakhont.callback.BaseCallbacks.Validator;
 * import akha.yakhont.location.LocationCallbacks.LocationClient;
 * import akha.yakhont.technology.Dagger2;
 * import akha.yakhont.technology.Dagger2.CallbacksValidationModule;
 * import akha.yakhont.technology.Dagger2.LocationModule;
 * import akha.yakhont.technology.Dagger2.Parameters;
 * import akha.yakhont.technology.Dagger2.UiModule;
 *
 * import dagger.BindsInstance;
 * import dagger.Component;
 * import dagger.Module;
 *
 * public class YourActivity extends Activity {
 *
 *     &#064;Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *
 *         Core.init(getApplication(), null, DaggerYourActivity_YourDagger
 *             .builder()
 *             .parameters(Parameters.create())
 *             .build()
 *         );
 *
 *         super.onCreate(savedInstanceState);
 *
 *         // your code here: setContentView(...), etc.
 *     }
 *
 *     // default implementation - customize only modules you need
 * //  &#064;Component(modules = {CallbacksValidationModule.class, LocationModule.class, UiModule.class})
 *
 *     &#064;Component(modules = {YourLocationModule.class, YourCallbacksValidationModule.class,
 *                                YourUiModule.class})
 *     interface YourDagger extends Dagger2 {
 *
 *         &#064;Component.Builder
 *         interface Builder {
 *             &#064;BindsInstance
 *             Builder parameters(Parameters parameters);
 *             YourDagger build();
 *         }
 *     }
 *
 *     // customize Yakhont callbacks validation here
 *     &#064;Module
 *     static class YourCallbacksValidationModule extends CallbacksValidationModule {
 *
 *         &#064;Override
 *         protected Validator getCallbacksValidator() {
 *             return super.getCallbacksValidator();
 *         }
 *     }
 *
 *     // customize Yakhont location client here
 *     &#064;Module
 *     static class YourLocationModule extends LocationModule {
 *
 *         &#064;Override
 *         protected LocationClient getLocationClient(boolean oldApi) {
 *             return super.getLocationClient(oldApi);
 *         }
 *     }
 *
 *     // customize Yakhont GUI here
 *     &#064;Module
 *     static class YourUiModule extends UiModule {
 *
 *         &#064;Override
 *         protected BaseDialog getPermissionAlert(Integer requestCode, Integer duration) {
 *             return super.getPermissionAlert(requestCode, duration);
 *         }
 *
 *         &#064;Override
 *         protected BaseDialog getToast(boolean useSnackbarIsoToast, Integer duration) {
 *             return super.getToast(useSnackbarIsoToast, duration);
 *         }
 *     }
 * }
 * </pre>
 *
 * @author akha
 */
public interface Dagger2 {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_ALERT_PERMISSION             = "alert_permission";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_ALERT_PERMISSION_DENIED      = "alert_permission_denied";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_LONG            = "toast_long";
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    String      UI_TOAST_LENGTH_SHORT           = "toast_short";

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PERMISSION)        Provider<BaseDialog>     getAlertPermission();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_ALERT_PERMISSION_DENIED) Provider<BaseDialog>     getAlertPermissionDenied();
    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Named(UI_TOAST_LENGTH_LONG)       Provider<BaseDialog>     getToastLong();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    @Named(UI_TOAST_LENGTH_SHORT)      Provider<BaseDialog>     getToastShort();

    /** @exclude */ @SuppressWarnings("JavaDoc")
    Validator            getCallbacksValidator();

    /** @exclude */ @SuppressWarnings("JavaDoc")
    Lazy<LocationClient> getLocationClient();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    @Component(modules = {LocationModule.class, UiModule.class, CallbacksValidationModule.class})
    interface DefaultComponent extends Dagger2 {
        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder parameters(Parameters parameters);
            DefaultComponent build();
        }
    }

    /**
     * The parameters defined at run-time.
     */
    class Parameters {

        private static final int     VALUE_LOCATION   = 1;
        // for using Snackbar instead of dialog alert
//      private static final int     VALUE_ALERT      = 2;
        private static final int     VALUE_TOAST      = 4;

        private        final int     mData;

        private static Parameters    sInstance;

        static {
            init();
        }

        /**
         * Cleanups static fields in Parameters; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            init();
        }

        private static void init() {
            sInstance = null;
        }

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
        @SuppressWarnings("WeakerAccess")
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
         *        {@code true} for {@link GoogleApiClient}-based Google Location API,
         *        {@code false} for {@link FusedLocationProviderClient}-based one
         *
         * @param useSnackbarIsoToast
         *        {@code true} for using {@link Snackbar} instead of {@link Toast}
         *
         * @return  The {@code Parameters} object
         */
        public static Parameters create(final boolean useGoogleLocationOldApi,
                                        final boolean useSnackbarIsoToast) {
            int flags = 0;

            if (useGoogleLocationOldApi) flags |= VALUE_LOCATION;
            if (useSnackbarIsoToast    ) flags |= VALUE_TOAST   ;

            return new Parameters(flags);
        }

        /**
         * Creates new {@code Parameters} object with default settings.
         *
         * @return  The {@code Parameters} object
         */
        public static Parameters create() {
            return create(false, false);
        }
    }

    /**
     * The callbacks annotations validation component.
     */
    @Module
    class CallbacksValidationModule {

        /**
         * Initialises a newly created {@code CallbacksValidationModule} object.
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public CallbacksValidationModule() {
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides
        public Validator provideCallbacksValidator() {
            return getCallbacksValidator();
        }

        /**
         * Creates new instance of the {@code Validator}.
         *
         * @return  The {@code Validator}
         */
        @SuppressWarnings("WeakerAccess")
        protected Validator getCallbacksValidator() {
            return new Validator() {
                @SuppressWarnings("rawtypes")
                @Override
                public boolean validate(Object object, Class<? extends BaseCallbacks>[] callbackClasses) {
                    if (callbackClasses == null || callbackClasses.length == 0) return true;

                    boolean result = true;
                    for (final Class<? extends BaseCallbacks> tmpClass: callbackClasses) {
                        final boolean resultTmp = validate(object, tmpClass);
                        if (!resultTmp) result = false;
                    }

                    CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR,
                            "callbacks annotations validation result " + result);
                    return result;
                }

                @SuppressWarnings("rawtypes")
                private boolean validate(Object object, Class<? extends BaseCallbacks> tmpClass) {
                    if (!tmpClass.equals(LocationCallbacks.class)) return true;

                    final boolean result = object instanceof Activity;
                    if (!result) CoreLogger.logError(
                            "LocationCallbacks should be used to annotate Activity only");

                    return result;
                }
            };
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
        @SuppressWarnings({"WeakerAccess", "unused"})
        public LocationModule() {
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides
        public LocationClient provideLocationClient(final Parameters parameters) {
            return getLocationClient(getFlagLocation(parameters));
        }

        /**
         * Creates new instance of the location client.
         *
         * @param oldApi
         *        {@code true} for {@link GoogleApiClient}-based Google Location API,
         *        {@code false} for {@link FusedLocationProviderClient}-based one
         *
         * @return  The location client
         */
        @SuppressWarnings("WeakerAccess")
        protected LocationClient getLocationClient(final boolean oldApi) {
            return oldApi ? new GoogleLocationClient(): new GoogleLocationClientNew();
        }

        private boolean getFlagLocation(final Parameters parameters) {
            return parameters.get(Parameters.VALUE_LOCATION);
        }
    }

    /**
     * The user interface component.
     */
    @SuppressWarnings({"JavadocReference", "unused"})
    @dagger.Module // strange bug
    class UiModule {

        /** Return code for {@link Callback#DISMISS_EVENT_TIMEOUT} (the value is {@value}). */
        public  static final int    SNACKBAR_DISMISSED_REASON_TIMEOUT       = Activity.RESULT_FIRST_USER;
        /** Return code for {@link Callback#DISMISS_EVENT_ACTION} (the value is {@value}). */
        public  static final int    SNACKBAR_DISMISSED_REASON_ACTION        = Activity.RESULT_FIRST_USER + 1;
        /** Return code for {@link Callback#DISMISS_EVENT_CONSECUTIVE} (the value is {@value}). */
        public  static final int    SNACKBAR_DISMISSED_REASON_CONSECUTIVE   = Activity.RESULT_FIRST_USER + 2;
        /** Return code for {@link Callback#DISMISS_EVENT_MANUAL} (the value is {@value}). */
        public  static final int    SNACKBAR_DISMISSED_REASON_MANUAL        = Activity.RESULT_FIRST_USER + 3;
        /** Return code for unknown Snackbar dismiss event (the value is {@value}). */
        public  static final int    SNACKBAR_DISMISSED_REASON_UNKNOWN       = Activity.RESULT_FIRST_USER + 4;

        private static final int    PERMISSION_RATIONALE_DURATION           =  16;      // seconds
        private static final int    PERMISSION_DENIED_DURATION              =   8;      // seconds

        private static final long   COUNTDOWN_LATCH_TIMEOUT                 = 128;      // about 2 minutes

        static {
            init();
        }

        /**
         * Cleanups UiModule; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            init();
        }

        // should be called on switching Activities
        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void cleanUp() {
            BaseSnackbar.clear();
        }

        private static void init() {
            BaseSnackbar.init();
        }

        /**
         * Initialises a newly created {@code UiModule} object.
         */
        public UiModule() {
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getPermissionResult(final int result) {
            switch (result) {
                case        SNACKBAR_DISMISSED_REASON_TIMEOUT               :
                    return "SNACKBAR_DISMISSED_REASON_TIMEOUT"              ;
                case        SNACKBAR_DISMISSED_REASON_ACTION                :
                    return "SNACKBAR_DISMISSED_REASON_ACTION"               ;
                case        SNACKBAR_DISMISSED_REASON_CONSECUTIVE           :
                    return "SNACKBAR_DISMISSED_REASON_CONSECUTIVE"          ;
                case        SNACKBAR_DISMISSED_REASON_MANUAL                :
                    return "SNACKBAR_DISMISSED_REASON_MANUAL"               ;
                case        SNACKBAR_DISMISSED_REASON_UNKNOWN               :
                    return "SNACKBAR_DISMISSED_REASON_UNKNOWN"              ;
                default:
                    return Utils.getActivityResultString(result);
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getSnackbarCallbackEvent(final int event) {
            switch (event) {
                case        Callback.DISMISS_EVENT_ACTION                   :
                    return "Snackbar.Callback.DISMISS_EVENT_ACTION"         ;
                case        Callback.DISMISS_EVENT_CONSECUTIVE              :
                    return "Snackbar.Callback.DISMISS_EVENT_CONSECUTIVE"    ;
                case        Callback.DISMISS_EVENT_MANUAL                   :
                    return "Snackbar.Callback.DISMISS_EVENT_MANUAL"         ;
                case        Callback.DISMISS_EVENT_SWIPE                    :
                    return "Snackbar.Callback.DISMISS_EVENT_SWIPE"          ;
                case        Callback.DISMISS_EVENT_TIMEOUT                  :
                    return "Snackbar.Callback.DISMISS_EVENT_TIMEOUT"        ;
                default:
                    return "unknown Snackbar.Callback event: " + Utils.getUnknownResult(event);
            }
        }

        /**
         * Checks Snackbar's queue status.
         *
         * @return  {@code true} if Snackbar's queue is empty, {@code false} otherwise
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public static boolean isSnackbarsQueueEmpty() {
            return BaseSnackbar.isQueueEmpty();
        }

        /**
         * If some Snackbar from Snackbar's queue is on the screen, returns it's text.
         *
         * @return  The Snackbar's text (or null)
         */
        public static String getSnackbarText() {
            return BaseSnackbar.getText();
        }

        /**
         * Combines {@link #getSnackbarText} and  {@link #isSnackbarsQueueEmpty}.
         *
         * @return  {@code true} if some Snackbar (from queue) is in screen (or still in queue), {@code false} otherwise
         */
        public static boolean hasSnackbars() {
            return BaseSnackbar.hasSnackbars();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean isSnackbar(final BaseDialog baseDialog) {
            return baseDialog instanceof BaseSnackbar;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_PERMISSION)
        public BaseDialog providePermissionAlert(final Parameters parameters) {
            return getPermissionAlert(Utils.getRequestCode(RequestCodes.PERMISSIONS_RATIONALE_ALERT),
                    PERMISSION_RATIONALE_DURATION);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_ALERT_PERMISSION_DENIED)
        public BaseDialog providePermissionDeniedAlert(final Parameters parameters) {
            return getPermissionAlert(Utils.getRequestCode(RequestCodes.PERMISSIONS_DENIED_ALERT),
                    PERMISSION_DENIED_DURATION);
        }

        /**
         * Creates new instance of the permissions alert component.
         *
         * @param requestCode
         *        The integer request code to pass to the {@link Activity#onActivityResult}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
         *        {@code Snackbar.LENGTH_LONG} or {@code Snackbar.LENGTH_SHORT}, null for default value
         *
         * @return  The alert component
         */
        protected BaseDialog getPermissionAlert(final Integer requestCode, final Integer duration) {
            final List<ViewModifier> viewModifiers = new ArrayList<>();
            viewModifiers.add(Utils.getDefaultSnackbarViewModifier());

            return new BaseSnackbar(duration, requestCode)
                    .setViewModifiers(viewModifiers)

                    .setActionText(R.string.yakhont_alert_ok)
                    .setActionTextColor(Utils.getDefaultSnackbarActionColor())
                    .setActionCountDownLatch(new CountDownLatch(1) {
                        @NonNull
                        @Override
                        public String toString() {
                            return "permission alert CountDownLatch";
                        }
                    }, COUNTDOWN_LATCH_TIMEOUT);
        }

        private static boolean handle(final boolean release, final BaseDialog baseDialog,
                                      final String id, final Collection<String> ids) {
            boolean result = false;

            if (baseDialog == null)
                CoreLogger.logWarning("baseDialog == null");

            else if (baseDialog instanceof BaseSnackbar) {
                final BaseSnackbar baseSnackbar = (BaseSnackbar) baseDialog;
                result = true;

                if (release)
                    baseSnackbar.removeAndRelease(ids);
                else
                    baseSnackbar.setId(id);
            }
            CoreLogger.log((release ? "release latch": "set ID") + " result: " + result);

            return result;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean setId(final BaseDialog baseDialog, final String id) {
            return handle(false, baseDialog, id, null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean releaseCountDownLatch(final BaseDialog baseDialog, final Collection<String> ids) {
            return handle(true, baseDialog, null, ids);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean releaseCountDownLatch(final BaseDialog baseDialog) {
            return releaseCountDownLatch(baseDialog, null);
        }

        private static boolean getFlagToast(final Parameters parameters) {
            return parameters.get(Parameters.VALUE_TOAST);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_TOAST_LENGTH_LONG)
        public BaseDialog provideLongToast(final Parameters parameters) {
            final boolean useSnackbarIsoToast = getFlagToast(parameters);
            return getToast(useSnackbarIsoToast,
                    useSnackbarIsoToast ? Snackbar.LENGTH_LONG: Toast.LENGTH_LONG);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @Provides @Named(UI_TOAST_LENGTH_SHORT)
        public BaseDialog provideShortToast(final Parameters parameters) {
            final boolean useSnackbarIsoToast = getFlagToast(parameters);
            return getToast(useSnackbarIsoToast,
                    useSnackbarIsoToast ? Snackbar.LENGTH_SHORT: Toast.LENGTH_SHORT);
        }

        /**
         * Creates new instance of a quick little text notification
         * (using {@link Toast} or {@link Snackbar}).
         *
         * @param useSnackbarIsoToast
         *        {@code true} for using {@link Snackbar} instead of {@link Toast}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
         *        {@code Snackbar.LENGTH_LONG}, {@code Snackbar.LENGTH_SHORT},
         *        {@code Toast.LENGTH_LONG} or {@code Toast.LENGTH_SHORT}, null for default value
         *
         * @return  {@link Toast}-based or {@link Snackbar}-based implementation
         */
        protected BaseDialog getToast(final boolean useSnackbarIsoToast, final Integer duration) {
            return useSnackbarIsoToast ?
                    new BaseSnackbar(duration, null): new BaseToast(duration);
        }

        private static boolean getFlag() {
            return getFlagToast(Parameters.getInstance());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToast(@StringRes final int resId, final Integer duration) {
            show(getFlag(), null, resId, duration);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToast(final String text, final Integer duration) {
            show(getFlag(), text, Core.NOT_VALID_RES_ID, duration);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToastExt(final Toast toast, final Integer duration) {
            new BaseToast(toast, duration, null).startToast(null,
                    Core.NOT_VALID_RES_ID, null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showToastExt(@LayoutRes final int viewId, final Integer duration) {
            new BaseToast(viewId, duration, null).startToast(null,
                    Core.NOT_VALID_RES_ID, null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Toast showToast(@LayoutRes final int viewLayoutId,
                                      @StringRes final int textId, final String text,
                                      final Integer duration, final Integer requestCode, final Intent data,
                                      final Integer gravity, final int xOffset, final int yOffset,
                                      final Float horizontalMargin, final float verticalMargin,
                                      final boolean show) {
            final String errMessage = getErrMessage("Toast", textId, text);

            final Toast[] toast = new Toast[] {null};
            try {
                final boolean result = new BaseToast(viewLayoutId, duration, requestCode)
                        .setGravity(gravity, xOffset, yOffset)
                        .setMargin (horizontalMargin, verticalMargin)
                        .startToast(text, textId, data, toast, show);

                if (!result) CoreLogger.logError(errMessage);
            }
            catch (Exception exception) {
                CoreLogger.log(errMessage, exception);
            }
            return toast[0];
        }

        private static String getErrMessage(final String type, @StringRes final int textId, final String text) {
            return String.format("can't start %s with text ID: %s, text: %s", type,
                    CoreLogger.getResourceDescription(textId), text);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Snackbar showSnackbar(final View view, @IdRes final Integer viewId,
                                            @StringRes final int textId, final String text, final Integer maxLines,
                                            final Integer duration, final Integer requestCode, final Intent data,
                                            @StringRes final int actionTextId, final String actionText,
                                            final View.OnClickListener actionListener, final ColorStateList actionColors,
                                            final @ColorRes int actionColorId, final @ColorInt int actionColor,
                                            final List<ViewModifier> viewModifiers, final boolean show) {
            final String errMessage = getErrMessage("Snackbar", textId, text);

            final Snackbar[] snackbar = new Snackbar[] {null};
            try {
                final boolean result = new BaseSnackbar(viewId, duration, requestCode)
                        .setText             (textId        )
                        .setMaxLines         (maxLines      )

                        .setView             (view          )
                        .setViewModifiers    (viewModifiers )

                        .setActionText       (actionText    )
                        .setActionText       (actionTextId  )

                        .setActionListener   (actionListener)
                        .setActionTextColor  (actionColor   )
                        .setActionTextColorId(actionColorId )
                        .setActionTextColor  (actionColors  )

                        .start(text, data, snackbar, show);

                if (!result) CoreLogger.logError(errMessage);
            }
            catch (Exception exception) {
                CoreLogger.log(errMessage, exception);
            }
            return snackbar[0];
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showSnackbar(final String text, final Integer duration) {
            show(true, text, Core.NOT_VALID_RES_ID, duration);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void showSnackbar(@StringRes final int resId, final Integer duration) {
            show(true, null, resId, duration);
        }

        private static void show(final boolean useSnackbarIsoToast, String text,
                                 @StringRes final int resId, final Integer duration) {
            if (text == null && !validate(resId)) return;
            if (text == null) text = Objects.requireNonNull(Utils.getApplication()).getString(resId);

            if (useSnackbarIsoToast)
                new BaseSnackbar(duration, null).start(text, null);
            else
                new BaseToast(duration).startToast(text, Core.NOT_VALID_RES_ID, null);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private static boolean validate(@StringRes final int resId) {
            try {
                //noinspection ConstantConditions
                return validate(Utils.getApplication().getString(resId));
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
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

        ////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Mainly for lambda support.
         */
        @SuppressWarnings("WeakerAccess")
        public interface ViewModifier {

            /**
             * Performs view customization.
             *
             * @param view
             *        The {@code View} to customize
             *
             * @param viewHandler
             *        The {@code ViewHandler}
             */
            void modify(View view, ViewHandler viewHandler);
        }

        /**
         * Intended to customize views (e.g. in {@code Toast}).
         */
        public static abstract class ViewHandler implements ViewModifier {

            /** @exclude */ @SuppressWarnings("JavaDoc")
            protected        View   mView;

            /**
             * Initialises a newly created {@code ViewHandler} object.
             */
            public ViewHandler() {
            }

            /**
             * Returns {@code TextView} for Yakhont's {@code Toast}.
             * Should be called from {@link #modify} only.
             *
             * @return  The {@code TextView}
             */
            public TextView getTextView() {
                return mView == null ? null: mView.findViewById(akha.yakhont.R.id.yakhont_loader_text);
            }

            /**
             * Returns {@code ProgressBar} for Yakhont's {@code Toast}.
             * Should be called from {@link #modify} only.
             *
             * @return  The {@code ProgressBar}
             */
            public ProgressBar getToastProgressView() {
                return mView == null ? null: mView.findViewById(akha.yakhont.R.id.yakhont_loader_progress);
            }

            /**
             * Performs view customization.
             *
             * @param view
             *        The {@code View} to customize
             *
             * @param viewHandler
             *        The {@code ViewHandler}
             */
            @Override
            public abstract void modify(final View view, final ViewHandler viewHandler);

            /** @exclude */ @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted"})
            public boolean wrapper(final View view) {
                mView = view;
                boolean result = true;

                if (view == null)
                    CoreLogger.logWarning("View handling, view == null");
                else
                    try {
                        modify(view, this);
                        CoreLogger.log("View handling was OK");
                    }
                    catch (Exception exception) {
                        CoreLogger.log("View handling failed", exception);
                        result = false;
                    }
                mView = null;

                return result;
            }
        }

        /**
         * Intended to customize {@code Snackbar's} views.
         */
        public static abstract class ViewHandlerSnackbar extends ViewHandler {

            private      TextView   mTextView;

            /**
             * Initialises a newly created {@code ViewHandlerSnackbar} object.
             */
            public ViewHandlerSnackbar() {
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted"})
            @Override
            public boolean wrapper(final View view) {
                final boolean result = super.wrapper(view);

                mTextView = null;
                return result;
            }

            /**
             * Sets the {@code TextView}. If not null, will be returned by {@link #getTextView(Snackbar)}.
             *
             * <p> After processing {@link #modify} this {@code TextView} will be reset and not valid anymore.
             *
             * @param textView
             *        The {@code TextView} to set
             */
            public void setTextView(final TextView textView) {
                mTextView = textView;
            }

            /**
             * Returns {@code TextView} for given {@code Snackbar}.
             *
             * @return  The {@code TextView}
             */
            public static TextView getTextView(final Snackbar snackbar) {
                if (snackbar != null) return getTextViewHelper(snackbar.getView());

                CoreLogger.logError("Snackbar == null");
                return null;
            }

            private static TextView getTextViewHelper(final View view) {
                //noinspection Convert2Lambda
                return (TextView) ViewHelper.findView(view, new ViewHelper.ViewVisitor() {
                    @Override
                    public boolean handle(final View viewTmp) {
                        return viewTmp instanceof TextView;
                    }
                });
            }

            /**
             * Returns {@code TextView} for Yakhont's {@code Snackbar}.
             * Should be called from {@link #modify} only.
             *
             * @return  The {@code TextView}
             */
            @Override
            public TextView getTextView() {
                return mTextView != null ? mTextView: getTextViewHelper(mView);
            }

            /**
             * Always returns null, should never be called.
             */
            @Override
            public ProgressBar getToastProgressView() {
                CoreLogger.logError("getToastProgressView() should be called for Toasts only");
                return null;
            }
        }
    }
}

class Helper {

    static void onActivityResult(final Activity activity, final Intent intent,
                                 final int requestCode,   final int resultCode) {
        if (activity == null)
            CoreLogger.logError("Helper.onActivityResult(): activity == null");
        else
            Utils.onActivityResult(activity, requestCode, resultCode, intent);
    }

    static void checkRequestCode(final Integer requestCode) {
        if (requestCode == null)
            CoreLogger.log("requestCode == null, onActivityResult() will not be called");
    }

    static void reportError() {
        CoreLogger.logError("data loading cancellation is not supported");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

class BaseSnackbar implements BaseDialog {

    private static class QueueEntry {

        @StringRes
        private    final int                            mTextId;
        private    final String                         mText;
        private    final String                         mTextDefault;

        @IdRes
        private    final Integer                        mViewId;

        @StringRes
        private    final int                            mActionStringId;
        private    final String                         mActionString;

        @ColorInt
        private    final int                            mColorAction;
        @ColorRes
        private    final int                            mColorIdAction;
        private    final ColorStateList                 mColorsAction;

        private    final Integer                        mRequestCode;
        private    final Intent                         mIntent;    // user info for onActivityResult
        private    final View.OnClickListener           mListener;
        private    final Integer                        mDuration;
        private    final Integer                        mMaxLines;
        private    final List<ViewModifier>             mViewModifiers;
        private    final Long                           mCountDownLatchTimeout;

        private          WeakReference<Snackbar>        mSnackbar;
        private    final CountDownLatch                 mCountDownLatch;
        private    final String                         mId;

        private QueueEntry(final String text, final String textDefault, @StringRes final int textId,
                           @IdRes final Integer viewId, final Integer requestCode, final Intent intent,
                           final String id, final View.OnClickListener listener, final Integer duration,
                           final String actionString, @StringRes int actionStringId,
                           final Integer maxLines, final List<ViewModifier> viewModifiers,
                           final ColorStateList colorsAction, @ColorInt final int colorAction,
                           @ColorRes final int colorIdAction,
                           final Long countDownLatchTimeout, final CountDownLatch countDownLatch) {
            mTextId                 = textId;
            mText                   = text;
            mTextDefault            = textDefault;

            mViewId                 = viewId;

            mActionStringId         = actionStringId;
            mActionString           = actionString;

            mColorAction            = colorAction;
            mColorIdAction          = colorIdAction;
            mColorsAction           = colorsAction;

            mRequestCode            = requestCode;
            mIntent                 = intent;
            mListener               = listener;
            mDuration               = duration;
            mMaxLines               = maxLines;
            mViewModifiers          = viewModifiers;
            mCountDownLatchTimeout  = countDownLatchTimeout;

            mCountDownLatch         = countDownLatch;
            mId                     = id;
        }

        private Snackbar validate() {
            final String              [] textFinal = new String              [1];
            final View.OnClickListener[] listener  = new View.OnClickListener[1];
            final Integer             [] duration  = new Integer             [1];

            listener[0] = mListener;
            duration[0] = mDuration;

            return makeSnackbar(mText, mTextDefault, mTextId, textFinal, null, mViewId, mRequestCode,
                    mIntent, listener, duration, mActionString, mActionStringId, mMaxLines, mViewModifiers,
                    mColorsAction, mColorAction, mColorIdAction, mCountDownLatchTimeout);
        }

        private Snackbar get() {
            Snackbar snackbar;
            if (mSnackbar != null) {
                snackbar = mSnackbar.get();
                if (snackbar != null) return snackbar;
            }
            snackbar = validate();
            if (snackbar != null) mSnackbar = new WeakReference<>(snackbar);
            return snackbar;
        }

        private void clear() {
            mSnackbar = null;
        }
    }

    private static final String                   UNKNOWN_TEXT      = "N/A";
    private static final int                      MAX_MAX_LINES     =     8;

    private static       LinkedList<QueueEntry>   sQueue;
    private static       QueueEntry               sQueueEntry;
    private static final Object                   sQueueLock;
    private static       Collection<String>       sStopList;

    @IdRes
    private        final Integer                  mViewId;
    private              View                     mView;

    private              Integer                  mDuration;
    private              Integer                  mMaxLines;

    private final        List<ViewModifier>       mViewModifiers    = new ArrayList<>();

    @ColorRes
    private              int                      mColorIdAction;
    @ColorInt
    private              int                      mColorAction;
    private              ColorStateList           mColorsAction;

    @StringRes
    private              int                      mStringId         = Core.NOT_VALID_RES_ID;
    private              String                   mString;

    @StringRes
    private              int                      mActionStringId   = Core.NOT_VALID_RES_ID;
    private              String                   mActionString;

    private              View.OnClickListener     mListener;
    private        final Integer                  mRequestCode;

    private              Intent                   mIntent;      // user info for onActivityResult

    private              CountDownLatch           mCountDownLatch;
    private              Long                     mCountDownLatchTimeout;
    private              String                   mId;

    static {
        sQueueLock                                                  = new Object();
        init();
    }

    static void init() {
        synchronized (sQueueLock) {
            if (sQueue != null) sQueue.clear();
            sQueue      = new LinkedList<>();

            sQueueEntry = null;
            sStopList   = null;
        }
    }

    static void clear() {
        synchronized (sQueueLock) {
            if (sQueueEntry != null) sQueueEntry.clear();
            if (sQueue != null)
                for (final QueueEntry entry: sQueue)
                    entry.clear();
        }
    }

    BaseSnackbar(final Integer duration, final Integer requestCode) {
        this(Core.NOT_VALID_VIEW_ID, duration, requestCode);
    }

    BaseSnackbar(@IdRes final Integer viewId, Integer duration, final Integer requestCode) {

        mViewId             = viewId;
        mRequestCode        = requestCode;

        Helper.checkRequestCode(requestCode);

        if (duration != null) {
            if (!isStandardDuration(duration) && duration <= 0) {
                CoreLogger.logError("wrong Snackbar duration " + duration);
                duration = Snackbar.LENGTH_SHORT;
            }
            mDuration = isStandardDuration(duration) ? duration : Core.adjustTimeout(duration);
        }
    }

    private static boolean isStandardDuration(final int duration) {
        return duration == Snackbar.LENGTH_INDEFINITE ||
               duration == Snackbar.LENGTH_LONG       ||
               duration == Snackbar.LENGTH_SHORT;
    }

    static boolean isQueueEmpty() {
        synchronized (sQueueLock) {
            return sQueue.isEmpty();
        }
    }

    static String getText() {
        synchronized (sQueueLock) {
            return getSnackbarText(sQueueEntry);
        }
    }

    static boolean hasSnackbars() {
        synchronized (sQueueLock) {
            return getText() != null || !isQueueEmpty();
        }
    }

    BaseSnackbar setView(final View view) {
        mView               = view;
        return this;
    }

    BaseSnackbar setMaxLines(final Integer maxLines) {
        mMaxLines           = maxLines;
        return this;
    }

    BaseSnackbar setText(@StringRes final int stringId) {
        mStringId           = stringId;
        return this;
    }

    @SuppressWarnings("unused")
    BaseSnackbar setText(final String string) {
        mString             = string;
        return this;
    }

    BaseSnackbar setActionText(@StringRes final int actionStringId) {
        mActionStringId     = actionStringId;
        return this;
    }

    BaseSnackbar setActionText(final String actionString) {
        mActionString       = actionString;
        return this;
    }

    BaseSnackbar setActionListener(final View.OnClickListener listener) {
        mListener           = listener;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    BaseSnackbar setId(final String id) {
        mId                 = id;
        return this;
    }

    @SuppressWarnings("unused")
    BaseSnackbar setActionCountDownLatch(final CountDownLatch countDownLatch, final Long timeout) {
        mCountDownLatch         = countDownLatch;
        mCountDownLatchTimeout  = timeout;
        return this;
    }

    void removeAndRelease(final Collection<String> stopList) {
        synchronized (sQueueLock) {
            if (stopList != null && stopList.size() > 0) {
                final Level level = CoreLogger.getDefaultLevel();
                    for (final String stop: stopList)
                        removeFromQueue(stop, level);
            }
            if (mCountDownLatch == null) return;

            sStopList = stopList;
        }
        CoreLogger.log("BaseSnackbar.removeAndRelease with stop list: " +
                (stopList == null ? null: Arrays.toString(stopList.toArray(new String[0]))));

        Utils.cancel(mCountDownLatch);
    }

    @SuppressWarnings("UnusedReturnValue")
    private static boolean removeFromQueue(final String id, final Level level) {
        boolean result = false;

        if (id == null) {
            CoreLogger.logError("id == null, can't remove from the Snackbar's queue");
            //noinspection ConstantConditions
            return result;
        }
        synchronized (sQueueLock) {
            for (int i = sQueue.size() - 1; i >= 0; i--)
                if (id.equals(sQueue.get(i).mId)) {
                    sQueue.remove(i);
                    result = true;
                }
        }
        if (result)
            CoreLogger.log("removed from Snackbar's queue, id == " + id);
        else
            CoreLogger.log(level, "can't remove from the Snackbar's queue, id == " + id);

        return result;
    }

    BaseSnackbar setActionTextColor(final ColorStateList colors) {
        mColorsAction       = colors;
        return this;
    }

    BaseSnackbar setActionTextColorId(@ColorRes final int id) {
        mColorIdAction      = id;
        return this;
    }

    BaseSnackbar setActionTextColor(@ColorInt final int color) {
        mColorAction        = color;
        return this;
    }

    BaseSnackbar setViewModifiers(final List<ViewModifier> viewModifiers) {
        mViewModifiers.clear();
        if (viewModifiers != null && viewModifiers.size() > 0)
            mViewModifiers.addAll(viewModifiers);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    private static void onActivityResult(final Intent intent,
                                         final Integer  requestCode, final int resultCode) {
        Helper.onActivityResult(Utils.getCurrentActivity(), intent, requestCode, resultCode);
    }

    @Override
    public boolean start(final String text, final Intent data) {
        final Snackbar[] snackbar = new Snackbar[] {null};
        final boolean    result   = start(text, data, snackbar, true);
        if (!result) CoreLogger.logError("can't start Snackbar with text: " +
                getSnackbarText(text, mString, mStringId));
        return result;
    }

    boolean start(final String text, final Intent data, final Snackbar[] snackbar,
                  final boolean show) {
        try {
            mIntent = data;
            return start(text, snackbar, show);
        }
        catch (Exception exception) {
            CoreLogger.log("failed Snackbar with text: " + getSnackbarText(text, mString, mStringId), exception);
            return false;
        }
    }

    // enable swipe-to-dismiss for non-CoordinatorLayout containers
    @SuppressLint("ClickableViewAccessibility")
    private static void enableSwipeToDismiss(final Activity activity, final Snackbar snackbar, final TextView textView) {
        if (snackbar.getView().getLayoutParams() instanceof CoordinatorLayout.LayoutParams) return;

        CoreLogger.log("enable Snackbar swipe-to-dismiss");
        textView.setOnTouchListener(new SwipeToDismissHandler(activity, snackbar));
    }

    private static class SwipeToDismissHandler extends GestureDetector.SimpleOnGestureListener
            implements View.OnTouchListener {

        private static final int                        THRESHOLD_X             = 64;
        private static final int                        THRESHOLD_VELOCITY      = 64;

        private              GestureDetector            mGestureDetector;
        private final        Snackbar                   mSnackbar;

        private SwipeToDismissHandler(final Context context, final Snackbar snackbar) {
            mSnackbar = snackbar;

            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    mGestureDetector = new GestureDetector(context, SwipeToDismissHandler.this);
                }

                @NonNull
                @Override
                public String toString() {
                    return "new GestureDetector()";
                }
            });
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(final View view, final MotionEvent event) {
            return mGestureDetector.onTouchEvent(event);
        }

        @Override
        public boolean onDown(final MotionEvent event) {
            return true;
        }

        @Override
        public boolean onFling(final MotionEvent event1, final MotionEvent event2,
                               final float velocityX, final float velocityY) {
            if (event2.getX() - event1.getX() >= THRESHOLD_X && velocityX >= THRESHOLD_VELOCITY)
                mSnackbar.dismiss();
            return true;
        }
    }

    private boolean start(final String text, final Snackbar[] result, final boolean show) {

        final String              [] textFinal = new String              [1];
        final View.OnClickListener[] listener  = new View.OnClickListener[1];
        final Integer             [] duration  = new Integer             [1];

        listener[0] = mListener;
        duration[0] = mDuration;

        final Snackbar snackbar = makeSnackbar(text, mString, mStringId, textFinal, mView, mViewId,
                mRequestCode, mIntent, listener, duration, mActionString, mActionStringId,
                mMaxLines, mViewModifiers, mColorsAction, mColorAction, mColorIdAction,
                mCountDownLatchTimeout);

        if (snackbar == null) return false;

        if (mRequestCode != null && mListener == null) mListener = listener[0];
        if (mDuration    == null                     ) mDuration = duration[0];

        result[0] = snackbar;

        if (show) synchronized (sQueueLock) {
            final QueueEntry entry = new QueueEntry(text, mString, mStringId, mViewId, mRequestCode,
                    mIntent, mId, mListener, mDuration, mActionString, mActionStringId, mMaxLines, mViewModifiers,
                    mColorsAction, mColorAction, mColorIdAction, mCountDownLatchTimeout, mCountDownLatch);
            if (mView == null && hasSnackbars()) {
                final boolean resultAdd = addNotSync(text, mString, mStringId, entry);
                if (!resultAdd)
                    CoreLogger.logError("can't add (not sync) Snackbar to queue, text: " +
                            getSnackbarText(text, mString, mStringId));
            }
            else {
                if (mView != null) clearQueue();
                show(result[0], mView != null ? null: entry);
            }
        }
        return true;
    }

    private static boolean addNotSync(final String text, final String textDefault, @StringRes final int textId,
                                      final QueueEntry entry) {
        if (entry.validate() == null) {
            CoreLogger.logError("Snackbar == null");
            return false;
        }

        if (sQueue.offer(entry))
            CoreLogger.log(text == null ? Level.WARNING: CoreLogger.getDefaultLevel(),
                    "Snackbar added to queue, text: " + getSnackbarText(text, textDefault, textId));
        else
            CoreLogger.logError("can't add Snackbar to queue, text: " + getSnackbarText(text, textDefault, textId));

        return true;
    }

    private static Snackbar makeSnackbar(final String text, final String textDefault,
                                         @StringRes final int textId, final String[] textFinal,
                                         View view, @IdRes final Integer viewId,
                                         final Integer requestCode, final Intent intent,
                                         final View.OnClickListener[] listener, final Integer[] duration,
                                         final String actionString, @StringRes int actionStringId,
                                         final Integer maxLines, final List<ViewModifier> viewModifiers,
                                         final ColorStateList colorsAction, @ColorInt final int colorAction,
                                         @ColorRes final int colorIdAction, final Long countDownLatchTimeout) {
        final Activity activity = Utils.getCurrentActivity();
        if (activity == null) {
            CoreLogger.logError("making Snackbar: activity == null");
            return null;
        }

        textFinal[0] = getSnackbarText(text, textDefault, textId);

        if (!Dagger2.UiModule.validate(textFinal[0])) return null;

        if (view != null && viewId != null && viewId != Core.NOT_VALID_VIEW_ID)
            CoreLogger.logError("View is already defined, so View ID " + CoreLogger.getResourceDescription(viewId) +
                    " will be ignored for Snackbar with text: " + textFinal[0]);

        if (view == null) view = ViewHelper.getViewForSnackbar(activity, viewId);
        if (view == null) {
            CoreLogger.logError("View is null, can not show Snackbar with text: " + textFinal[0]);
            return null;
        }

        if (requestCode != null && listener[0] == null)
            //noinspection Convert2Lambda
            listener[0] = new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    onActivityResult(intent, requestCode, Activity.RESULT_OK);
                }
            };

        if (duration[0] == null) {
            if (listener[0] != null)
                duration[0] = Snackbar.LENGTH_INDEFINITE;
            else {
                CoreLogger.logError("duration is not defined, set to LENGTH_LONG " +
                        "for Snackbar with text: " + textFinal[0]);
                duration[0] = Snackbar.LENGTH_LONG;
            }
        }

        final Snackbar snackbar = Snackbar.make(view, textFinal[0], duration[0]);

        boolean actionSet = false;
        if (listener[0] != null) {
            if (actionStringId != Core.NOT_VALID_RES_ID && actionString != null)
                CoreLogger.logWarning("Both action string and action string ID were set; " +
                        "action string ID will be ignored for Snackbar with text: " + textFinal[0]);

            if (actionString != null)
                snackbar.setAction(actionString, listener[0]);
            else {
                if (actionStringId == Core.NOT_VALID_RES_ID)
                    CoreLogger.logError("neither action string nor action string ID were " +
                            "not defined for Snackbar with text: " + textFinal[0]);
                else {
                    snackbar.setAction(actionStringId, listener[0]);
                    actionSet = true;
                }
            }
        }

        final TextView textView = ViewHandlerSnackbar.getTextView(snackbar);
        if (textView == null)
            CoreLogger.logError("can't find TextView for Snackbar with text: " + textFinal[0]);
        else {
            if (maxLines != null)
                if (maxLines > 0 && maxLines <= MAX_MAX_LINES)
                    textView.setMaxLines(maxLines);
                else
                    CoreLogger.logError("wrong maxLines " + maxLines +
                            " for Snackbar's TextView with text: " + textFinal[0]);

            enableSwipeToDismiss(activity, snackbar, textView);
        }

        final ViewHandlerSnackbar viewHandler = viewModifiers.size() == 0 ? null:
                new UiModule.ViewHandlerSnackbar() {
                    @SuppressWarnings("unused")
                    @Override
                    public void modify(final View view, final UiModule.ViewHandler viewHandler) {
                        for (int i = 0; i < viewModifiers.size(); i++)
                            viewModifiers.get(i).modify(view, viewHandler);
                    }
                };
        if (viewHandler != null) {
            viewHandler.setTextView(textView);

            if (!viewHandler.wrapper(snackbar.getView()))
                CoreLogger.log("failed View customization for Snackbar with text: " + textFinal[0]);
        }

        if (colorsAction != null) {
            if (colorAction != Core.NOT_VALID_COLOR || colorIdAction != Core.NOT_VALID_RES_ID)
                CoreLogger.logWarning("ColorStateList != null so Snackbar action color " +
                        "will be ignored, Snackbar text: " + textFinal[0]);
            snackbar.setActionTextColor(colorsAction);
        }
        else if (colorAction  != Core.NOT_VALID_COLOR ) {
            if (colorIdAction != Core.NOT_VALID_RES_ID)
                CoreLogger.logWarning("mColorAction != null so Snackbar action color ID " +
                        "will be ignored, Snackbar text: " + textFinal[0]);
            snackbar.setActionTextColor(colorAction);
        }
        else if (colorIdAction != Core.NOT_VALID_RES_ID)
            snackbar.setActionTextColor(ContextCompat.getColor(activity, colorIdAction));

        snackbar.addCallback(new BaseCallback(requestCode, intent, countDownLatchTimeout,
                actionSet, new Callable<Collection<String>>() {
            @Override
            public Collection<String> call() {
                synchronized (sQueueLock) {
                    final Collection<String> tmp = sStopList;
                    sStopList = null;
                    return tmp;
                }
            }

            @NonNull
            @Override
            public String toString() {
                return "clear snackbar StopList";
            }
        }) {
            @SuppressLint("SwitchIntDef")
            @Override
            public void onDismissed(final Snackbar snackbar, final int event) {
                synchronized (sQueueLock) {
                    sQueueEntry = null;
                }
                super.onDismissed(snackbar, event);
            }
        });

        return snackbar;
    }

    private static void show(final Snackbar snackbar, final QueueEntry entry) {
        sQueueEntry = entry;
        snackbar.show();
    }

    private static void clearQueue() {
        if (sQueue == null || sQueue.size() == 0) return;

        final List<String> list = new ArrayList<>();
        for (final QueueEntry entry: sQueue) {
            final String text = getSnackbarText(entry);
            list.add(text != null ? text: "text is " + UNKNOWN_TEXT);
        }
        if (list.size() > 0)
            CoreLogger.logError("the following queued Snackbars will be removed " +
                    "'cause some Snackbar broke the Snackbars queue: " +
                    Arrays.toString(list.toArray(new String[0])));

        sQueue.clear();
    }

    private static class BaseCallback extends Snackbar.Callback {

        private     final Integer                       mRequestCode;
        private     final Intent                        mIntent;
        private     final boolean                       mActionNoDefaultHandler;
        private     final Long                          mCountDownLatchTimeout;
        private     final Callable<Collection<String>>  mStopList;

        private BaseCallback(final Integer requestCode, final Intent intent, final Long timeout,
                             final boolean actionNoDefaultHandler, final Callable<Collection<String>> stopList) {
            mRequestCode                = requestCode;
            mIntent                     = intent;
            mCountDownLatchTimeout      = timeout;
            mActionNoDefaultHandler     = actionNoDefaultHandler;
            mStopList                   = stopList;
        }

        @SuppressLint("SwitchIntDef")
        @Override
        public void onDismissed(final Snackbar snackbar, final int event) {

            CoreLogger.log(event == Callback.DISMISS_EVENT_CONSECUTIVE ? Level.ERROR:
                    CoreLogger.getDefaultLevel(), "Snackbar.Callback event: " + UiModule.getSnackbarCallbackEvent(event));

            switch (event) {
                // indicates that the Snackbar was dismissed from a new Snackbar being shown
                case Callback.DISMISS_EVENT_CONSECUTIVE:
                    onDismiss(UiModule.SNACKBAR_DISMISSED_REASON_CONSECUTIVE);

                    synchronized (sQueueLock) {
                        clearQueue();
                        sQueueEntry = null;
                    }
                    break;

                // indicates that the Snackbar was dismissed via an action click
                case Callback.DISMISS_EVENT_ACTION:
                    onDismiss(UiModule.SNACKBAR_DISMISSED_REASON_ACTION);
                    break;

                // indicates that the Snackbar was dismissed via a call to dismiss()
                case Callback.DISMISS_EVENT_MANUAL:
                    onDismiss(UiModule.SNACKBAR_DISMISSED_REASON_MANUAL);
                    break;

                // indicates that the Snackbar was dismissed via a swipe
                case Callback.DISMISS_EVENT_SWIPE:
                    onDismiss(Activity.RESULT_CANCELED);
                    break;

                // indicates that the Snackbar was dismissed via a timeout
                case Callback.DISMISS_EVENT_TIMEOUT:
                    onDismiss(UiModule.SNACKBAR_DISMISSED_REASON_TIMEOUT);
                    break;

                default:        // should never happen
                    CoreLogger.logError("unknown Snackbar.Callback event: " + event);

                    onDismiss(UiModule.SNACKBAR_DISMISSED_REASON_UNKNOWN);
                    break;
            }

            super.onDismissed(snackbar, event);
        }

        private void onDismiss(final int code) {
            if (mRequestCode != null && !(
                    code == UiModule.SNACKBAR_DISMISSED_REASON_ACTION && mActionNoDefaultHandler))
                Helper.onActivityResult(Utils.getCurrentActivity(), mIntent, mRequestCode, code);

            if (code == UiModule.SNACKBAR_DISMISSED_REASON_CONSECUTIVE) return;

            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    final QueueEntry entry;
                    synchronized (sQueueLock) {
                        entry = onDismissAsync();
                    }
                    if (entry == null) return;

                    final String text = getSnackbarText(entry);

                    if (entry.mCountDownLatch != null) {
                        CoreLogger.log("Snackbar waiting on latch, text: " + text);
                        Utils.await(entry.mCountDownLatch, mCountDownLatchTimeout);

                        CoreLogger.log("Snackbar waiting on latch released, text: " + text);
                        if (mStopList != null && entry.mId != null) {
                            Collection<String> stopList = null;
                            try {
                                stopList = mStopList.call();
                            }
                            catch (Exception exception) {           // should never happen
                                CoreLogger.log(exception);
                            }
                            if (stopList != null)
                                for (final String stop: stopList)
                                    if (entry.mId.equals(stop)) {
                                        CoreLogger.log("Snackbar from queue cancelled, text: " + text);
                                        return;
                                    }
                        }
                    }
                    show(entry.get(), entry);

                    CoreLogger.log("Snackbar shown from queue, text: " + text);
                }

                @NonNull
                @Override
                public String toString() {
                    return "show(Snackbar)";
                }
            });
        }

        private QueueEntry onDismissAsync() {
            sQueueEntry = null;
            return sQueue.poll();
        }
    }

    private static String getSnackbarText(final QueueEntry entry) {
        return entry == null ? null: getSnackbarText(entry.mText, entry.mTextDefault, entry.mTextId);
    }

    private static String getSnackbarText(final String text, final String textDefault, @StringRes final int textId) {
        try {
            return text != null ? text: textDefault != null ? textDefault: textId != Core.NOT_VALID_RES_ID
                    ? Objects.requireNonNull(Utils.getApplication()).getString(textId): null;
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
            return null;
        }
    }

    @Override
    public void stop() {
        try {
            if (mCountDownLatch != null) {
                Utils.cancel(mCountDownLatch);
                mCountDownLatch  = null ;
            }
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }

        try {
            synchronized (sQueueLock) {
                if (sQueueEntry != null) {
                    if (sQueueEntry.mSnackbar != null) {
                        final Snackbar snackbar = sQueueEntry.mSnackbar.get();
                        if (snackbar != null) snackbar.dismiss();
                    }
                    sQueueEntry  = null;
                }
            }
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
    }

    @Override
    public boolean confirm(final Activity context, final View view) {
        Helper.reportError();
        return false;
    }

    @Override
    public boolean setOnCancel(final Runnable runnable) {
        Helper.reportError();
        return false;
    }

    @Override
    public boolean cancel() {
        stop();
        Helper.reportError();
        return false;
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

class BaseToast implements BaseDialog {

    private static final int                      DELAY_STANDARD    = 3000;
    private static final int                      DELAY_ADD         =  500;

    private static final int                      UPDATE_INTERVAL   =  300;

    private              Toast                    mToast;
    private        final Integer                  mRequestCode;

    @LayoutRes
    private        final int                      mViewLayoutId;

    @NonNull
    private        final Integer                  mDuration;
    private              Integer                  mGravity;
    private              int                      mXOffset;
    private              int                      mYOffset;

    private              Float                    mHorizontalMargin;
    private              float                    mVerticalMargin;

    private              CountDownTimer           mCountDownTimer;

    BaseToast(final Integer duration) {
        this(Core.NOT_VALID_RES_ID, duration);
    }

    BaseToast(@LayoutRes final int viewLayoutId, final Integer duration, final Integer requestCode) {
        this(null, viewLayoutId, duration, requestCode);
    }

    BaseToast(@NonNull final Toast toast, final Integer duration,
              @SuppressWarnings("SameParameterValue") final Integer requestCode) {
        this(toast, Core.NOT_VALID_RES_ID, duration, requestCode);
    }

    private BaseToast(@SuppressWarnings("SameParameterValue") @LayoutRes final int viewLayoutId,
                      final Integer duration) {
        this(viewLayoutId, duration, null);
    }

    private BaseToast(final Toast toast, @LayoutRes final int viewLayoutId,
                      Integer duration, final Integer requestCode) {

        if (duration != null && toast != null)
            toast.setDuration(isStandardDuration(duration) ? duration: Toast.LENGTH_SHORT);

        if (duration == null) duration = Toast.LENGTH_SHORT;
        if (!isStandardDuration(duration) && duration <= 0) {
            CoreLogger.logError("wrong Toast duration " + duration);
            duration = Toast.LENGTH_SHORT;
        }

        mDuration         = isStandardDuration(duration) ? duration: Core.adjustTimeout(duration);
        mRequestCode      = requestCode;
        mViewLayoutId     = viewLayoutId;
        mToast            = toast;

        Helper.checkRequestCode(requestCode);
    }

    BaseToast setGravity(final Integer gravity, final int xOffset, final int yOffset) {
        mGravity          = gravity;
        mXOffset          = xOffset;
        mYOffset          = yOffset;
        return this;
    }

    BaseToast setMargin(final Float horizontalMargin, final float verticalMargin) {
        mHorizontalMargin = horizontalMargin;
        mVerticalMargin   = verticalMargin;
        return this;
    }

    boolean startToast(final String text,
                       @SuppressWarnings("SameParameterValue") @StringRes final int textId,
                       final Intent data) {
        return startToast(text, textId, data, new Toast[] {null}, true);
    }

    private boolean noView() {
        return mViewLayoutId == Core.NOT_VALID_RES_ID;
    }

    private String getToastDescription(final String text) {
        return "text - " + text + ", view layout ID - " + CoreLogger.getResourceDescription(mViewLayoutId);
    }

    @SuppressLint("ShowToast")
    boolean startToast(String text, @StringRes final int textId, final Intent data,
                       final Toast[] toast, final boolean show) {
        final Context context = Utils.getApplication();

        if (text == null && noView())
            if (textId == Core.NOT_VALID_RES_ID) {
                CoreLogger.logError("no text for Toast");
                return false;
            }
            else
                text = Objects.requireNonNull(context).getString(textId);

        if (mToast == null && noView() && !Dagger2.UiModule.validate(text)) return false;

        try {
            if (mToast == null) {
                if (Utils.isCurrentThreadMain())
                    makeToast(context, text);
                else {
                    final String         textFinal      = text;
                    final CountDownLatch countDownLatch = new CountDownLatch(1);

                    Utils.postToMainLoop(new Runnable() {
                        @Override
                        public void run() {
                            makeToast(context, textFinal);
                            countDownLatch.countDown();
                        }

                        @NonNull
                        @Override
                        public String toString() {
                            return "makeToast";
                        }
                    });
                    Utils.await(countDownLatch);
                }
                if (mToast == null) return false;

                try {
                    if (mGravity          != null)
                        mToast.setGravity(mGravity, mXOffset, mYOffset);

                    if (mHorizontalMargin != null)
                        mToast.setMargin(mHorizontalMargin, mVerticalMargin);
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                }
            }

            toast[0] = mToast;
            if (!show) return true;

            if (isStandardDuration(mDuration))
                mToast.show();
            else
                mCountDownTimer = new CountDownTimer(mDuration, UPDATE_INTERVAL) {
                    @Override
                    public void onTick(final long millisUntilFinished) {
                        mToast.show();
                    }

                    @Override
                    public void onFinish() {
                        mToast.cancel();

                        mToast          = null;
                        mCountDownTimer = null;
                    }
                }.start();

            if (mRequestCode != null) {
                final Activity activity = Utils.getCurrentActivity();

                if (activity != null) {
                    final int delay = isStandardDuration(mDuration) ? DELAY_STANDARD: mDuration + DELAY_ADD;
                    CoreLogger.log("BaseToast onActivityResult() delay " + delay +
                            " for Toast: " + getToastDescription(text));

                    Utils.runInBackground(delay, new Runnable() {
                        @Override
                        public void run() {
                            Helper.onActivityResult(activity, data, mRequestCode, Activity.RESULT_OK);
                        }

                        @NonNull
                        @Override
                        public String toString() {
                            return "Toast - onActivityResult()";
                        }
                    });
                }
                else
                    CoreLogger.logError("there's no Activity, onActivityResult() will not be called " +
                            "for Toast: " + getToastDescription(text));
            }

            return true;
        }
        catch (Exception exception) {
            CoreLogger.log("failed Toast: " + getToastDescription(text), exception);
            return false;
        }
    }

    @SuppressLint("ShowToast")
    private void makeToast(final Context context, final String text) {
        if (mViewLayoutId != Core.NOT_VALID_RES_ID) {
            mToast = new Toast(context);
            mToast.setView(LayoutInflater.from(context).inflate(
                    mViewLayoutId, null, false));
            mToast.setDuration(getDuration());
        }
        else
            try {
                mToast = Toast.makeText(context, text, getDuration());
            }
            catch (Exception exception) {
                CoreLogger.log("looks like a Toast.makeText() bug for Toast: " + getToastDescription(text) +
                        ", context: " + CoreLogger.getDescription(context), exception);
            }
    }

    private static boolean isStandardDuration(final int duration) {
        return duration == Toast.LENGTH_LONG || duration == Toast.LENGTH_SHORT;
    }

    private int getDuration() {
        return isStandardDuration(mDuration) ? mDuration: Toast.LENGTH_SHORT;
    }

    @Override
    public boolean start(final String text, final Intent data) {
        return startToast(text, Core.NOT_VALID_RES_ID, data);
    }

    @Override
    public void stop() {
        try {
            if (mCountDownTimer != null) {
                mCountDownTimer.cancel();
                mCountDownTimer  = null ;
            }
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }

        try {
            if (mToast != null) {
                mToast.cancel();
                mToast  = null ;
            }
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
    }

    @Override
    public boolean confirm(final Activity context, final View view) {
        Helper.reportError();
        return false;
    }

    @Override
    public boolean setOnCancel(final Runnable runnable) {
        Helper.reportError();
        return false;
    }

    @Override
    public boolean cancel() {
        stop();
        Helper.reportError();
        return false;
    }
}
