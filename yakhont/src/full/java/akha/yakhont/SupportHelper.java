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

package akha.yakhont;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.LayoutRes;
import android.support.annotation.Size;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentActivity;

/** @exclude */ @SuppressWarnings("JavaDoc")
@SuppressLint("ObsoleteSdkInt")
public class SupportHelper {                    // full

    private SupportHelper() {
    }
    
    public static boolean isSupportMode(final Activity context) {
        return context instanceof FragmentActivity;
    }
    
    public static android.app.FragmentManager getFragmentManager(final Activity activity, final android.app.Fragment fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            return activity.getFragmentManager();
        
        CoreLogger.logError("getFragmentManager() not defined");
        return null;
    }
    
    public static android.support.v4.app.FragmentManager getFragmentManager(final Activity activity, final android.support.v4.app.Fragment fragment) {
        return ((FragmentActivity) activity).getSupportFragmentManager();
    }
    
    public static void enableFragmentManagerDebugLogging(final boolean enable) {
        akha.yakhont.support.debug.BaseFragment.enableFragmentManagerDebugLogging(enable);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            akha.yakhont    .debug.BaseFragment.enableFragmentManagerDebugLogging(enable);
    }
    
    public static void enableLoaderManagerDebugLogging(final boolean enable) {
        akha.yakhont.support.loader.BaseLoader.enableLoaderManagerDebugLogging(enable);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            akha.yakhont    .loader.BaseLoader.enableLoaderManagerDebugLogging(enable);
    }
    
    public static BaseCursorAdapter getBaseCursorAdapter(@NonNull final Activity context,
                                                         @LayoutRes final int layoutId,
                                                         @NonNull @Size(min = 1) final String[]          from,
                                                         @NonNull @Size(min = 1) final    int[]          to) {
        if (isSupportMode(context))
            if (akha.yakhont.support.adapter.BaseSimpleCursorAdapter.isSupport() ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                return new akha.yakhont.support.adapter.BaseSimpleCursorAdapter(context, layoutId, from, to);
            else
                return new akha.yakhont.support.adapter.BaseSimpleCursorAdapter(context, layoutId, from, to, 0);

        if (akha.yakhont.adapter.BaseSimpleCursorAdapter.isSupport() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            return new akha.yakhont.adapter.BaseSimpleCursorAdapter(context, layoutId, from, to);
        else
            return new akha.yakhont.adapter.BaseSimpleCursorAdapter(context, layoutId, from, to, 0);
    }
    
    public static BaseActivityCallbacks getWorkerFragmentCallbacks() {
        return new WorkerFragmentCallbacksFull();
    }

    public static void registerValidateFragmentCallbacks() {
        akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.register(
                new akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.ValidateFragmentCallbacks());
        akha.yakhont.support.callback.lifecycle.BaseFragmentLifecycleProceed.register(
                new akha.yakhont.support.callback.lifecycle.BaseFragmentLifecycleProceed.ValidateFragmentCallbacks());
    }

    public static BaseDialog getAlert(@StringRes final int resId, final int requestCode, final Boolean yesNo) {
        return new AlertFull(resId, requestCode, yesNo);
    }

    public static BaseDialog getProgress() {
        return new ProgressFull();
    }

    public static void showLocationErrorDialog(final Activity activity, final int errorCode) {
        if (isSupportMode(activity))
            akha.yakhont.support.fragment.dialog.CommonDialogFragment.showLocationErrorDialog(activity, errorCode);
        
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            akha.yakhont        .fragment.dialog.CommonDialogFragment.showLocationErrorDialog(activity, errorCode);
                
        else
            CoreLogger.logError("CommonDialogFragment not defined");
    }

    private static class WorkerFragmentCallbacksFull extends BaseActivityCallbacks {
        
        private final akha.yakhont.support.fragment.WorkerFragment.WorkerFragmentCallbacks
                                    mCallbacksSupport = new akha.yakhont.support.fragment.WorkerFragment.WorkerFragmentCallbacks();
        private final akha.yakhont        .fragment.WorkerFragment.WorkerFragmentCallbacks
                                    mCallbacks        = new akha.yakhont        .fragment.WorkerFragment.WorkerFragmentCallbacks();
        
        @Override
        public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
            if (isSupportMode(activity))
                mCallbacksSupport.onActivityCreated(activity, savedInstanceState);
            
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                mCallbacks       .onActivityCreated(activity, savedInstanceState);
            
            else
                CoreLogger.logError("WorkerFragmentCallbacks not defined");
        }
    }

    private static class AlertFull extends BaseFull {
        
        @StringRes 
        private final int           mResId;
        private final int           mRequestCode;
        private final Boolean       mYesNo;

        public AlertFull(@StringRes final int resId, final int requestCode, final Boolean yesNo) {
            mResId          = resId;
            mRequestCode    = requestCode;
            mYesNo          = yesNo;
        }
        
        @Override
        public boolean start(final Activity context, final String text, final Intent data) {
            mDialog = isSupportMode(context)                           ?
                akha.yakhont.support.fragment.dialog.CommonDialogFragment.getDaggerAlert(mResId, mRequestCode, mYesNo):
                        
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    akha.yakhont    .fragment.dialog.CommonDialogFragment.getDaggerAlert(mResId, mRequestCode, mYesNo): null;
                            
            return super.start(context, text, data);
        }
    }

    private static class ProgressFull extends BaseFull {

        @Override
        public boolean start(final Activity context, final String text, final Intent data) {
            mDialog = isSupportMode(context)                           ?
                akha.yakhont.support.fragment.dialog.CommonDialogFragment.getDaggerProgress():
            
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    akha.yakhont    .fragment.dialog.CommonDialogFragment.getDaggerProgress(): null;
                                              
            return super.start(context, text, data);
        }
    }

    private abstract static class BaseFull implements BaseDialog {

        protected BaseDialog        mDialog;

        @Override
        public boolean start(final Activity context, final String text, final Intent data) {
            if (mDialog == null) CoreLogger.logError("start: mDialog == null");
            return mDialog == null ? false: mDialog.start(context, text, data);
        }

        @Override
        public boolean stop() {
            if (mDialog == null) CoreLogger.logError("stop: mDialog == null");
            return mDialog == null ? false: mDialog.stop();
        }
    }
}
