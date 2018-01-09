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

package akha.yakhont;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseSimpleCursorAdapter;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.ValidateFragmentCallbacks;
import akha.yakhont.debug.BaseFragment;
import akha.yakhont.fragment.WorkerFragment.WorkerFragmentCallbacks;
import akha.yakhont.fragment.dialog.CommonDialogFragment;
import akha.yakhont.loader.BaseLoader;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.LayoutRes;
import android.support.annotation.Size;
import android.support.annotation.StringRes;

/** @exclude */ @SuppressWarnings("JavaDoc")
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SupportHelper {                    // core

    private SupportHelper() {
    }
    
    @SuppressWarnings({"SameReturnValue", "UnusedParameters"})
    public static boolean isSupportMode(final Activity context) {
        return false;
    }
    
    @SuppressWarnings("UnusedParameters")
    public static FragmentManager getFragmentManager(final Activity activity, final Fragment fragment) {
        return activity.getFragmentManager();
    }
    
    public static void enableFragmentManagerDebugLogging(final boolean enable) {
        BaseFragment.enableFragmentManagerDebugLogging(enable);
    }
    
    public static void enableLoaderManagerDebugLogging(final boolean enable) {
        BaseLoader.enableLoaderManagerDebugLogging(enable);
    }
    
    public static BaseCursorAdapter getBaseCursorAdapter(@NonNull final Activity context, @LayoutRes final int layoutId,
                                                         @NonNull @Size(min = 1) final String[]          from,
                                                         @NonNull @Size(min = 1) final    int[]          to) {
        return new BaseSimpleCursorAdapter(context, layoutId, from, to);
    }
    
    public static BaseActivityCallbacks getWorkerFragmentCallbacks() {
        return new WorkerFragmentCallbacks();
    }

    public static void registerValidateFragmentCallbacks() {
        BaseFragmentLifecycleProceed.register(new ValidateFragmentCallbacks());
    }

    public static void showLocationErrorDialog(final Activity activity, final int errorCode) {
        CommonDialogFragment.showLocationErrorDialog(activity, errorCode);
    }

    public static BaseDialog getAlert(@StringRes final int resId, final int requestCode, final Boolean yesNo) {
        return CommonDialogFragment.getDaggerAlert(resId, requestCode, yesNo);
    }
    
    public static BaseDialog getProgress() {
        return CommonDialogFragment.getDaggerProgress();
    }
}
