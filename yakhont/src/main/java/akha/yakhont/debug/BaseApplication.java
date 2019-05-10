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

package akha.yakhont.debug;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.LogDebug;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

/**
 * The <code>BaseApplication</code> class is intended for debug purposes.
 * Overridden methods most of the time just adds some logging.
 *
 * @see LogDebug
 *
 * @author akha
 */
@SuppressLint("Registered")
@SuppressWarnings("unused")
public class BaseApplication extends Application {

    /**
     * Initialises a newly created {@code BaseApplication} object.
     */
    public BaseApplication() {
    }

    /**
     * Override to change the logging message.
     *
     * @return  The logging message (for debugging)
     */
    @SuppressWarnings("SameReturnValue")
    protected String getDebugMessage() {
        return "debug";
    }

    /**
     * Override to change the logging level.
     * <br>The default value is {@link CoreLogger#getDefaultLevel()}.
     *
     * @return  The logging priority level (for debugging)
     */
    @SuppressWarnings("SameReturnValue")
    protected Level getDebugLevel() {
        return CoreLogger.getDefaultLevel();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", newConfig " + newConfig, false);

        super.onConfigurationChanged(newConfig);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCreate() {
        setStrictMode(this, "application");

        CoreLogger.log(getDebugLevel(), "application " + getClass().getName() + " started", false);

        super.onCreate();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onLowMemory() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onLowMemory();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onTerminate() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onTerminate();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onTrimMemory(int level) {
        CoreLogger.log(getDebugLevel(), "level " + level, false);

        super.onTrimMemory(level);
    }

    /**
     * Sets {@link StrictMode} (in debug builds only, see {@link Utils#isDebugMode Utils.isDebugMode()}).
     *
     * @param application
     *        The application
     *
     * @param info
     *        The info for logging
     *
     * @return  {@code true} if {@link StrictMode} was enabled, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setStrictMode(@NonNull final Application application, @NonNull final String info) {
        return setStrictMode(application.getPackageName(), info);
    }

    /**
     * Sets {@link StrictMode} (in debug builds only, see {@link Utils#isDebugMode Utils.isDebugMode()}).
     *
     * @param packageName
     *        The name of application's package
     *
     * @param info
     *        The info for logging
     *
     * @return  {@code true} if {@link StrictMode} was enabled, {@code false} otherwise
     */
    @SuppressLint("ObsoleteSdkInt")
    public static boolean setStrictMode(@NonNull final String packageName, @NonNull final String info) {
        if (!Utils.isDebugMode(packageName)) return false;

        CoreLogger.log(Level.ERROR, "--- STRICT MODE --- (" + info + ")", false);

        final StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder()
                .detectAll()
//                .penaltyDialog()
                .penaltyLog();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) builder
                .penaltyFlashScreen();

        StrictMode.setThreadPolicy(builder.build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        return true;
    }
}
