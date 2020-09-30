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

package akha.yakhont.demoservice;

import akha.yakhont.demoservice.model.Data;
import akha.yakhont.demoservice.retrofit.LocalOkHttpClient2;
import akha.yakhont.demoservice.retrofit.Retrofit2Api;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.LogDebug;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallbacks;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MainService extends JobIntentService implements ViewModelStoreOwner {

    public  static final int     JOB_ID = 1001;
    private static final String  TAG    = "yakhont";

    private final ViewModelStore mViewModelStore = new ViewModelStore();

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return mViewModelStore;
    }

    // by default @LogDebug works with debug builds only
    @LogDebug                       // debug method demo (logs method's parameters and return value)
    @Override
    public void onCreate() {
        // uncomment if you're going to use Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      Core.setRxUncaughtExceptionBehavior(false);    // not terminate

        super.onCreate();

        CoreLogger.setLogLevel(Level.WARNING);          // for @LogDebug(Level.WARNING) below

        demoWeaving();
    }

    // by default @LogDebug works with debug builds only
    @LogDebug(Level.WARNING)        // debug method demo (logs method's parameters and return value)
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        LoaderCallbacks<Throwable, List<Data>> loaderCallbacks =
                new LoaderCallbacks<Throwable, List<Data>>() {
                    @Override
                    public void onLoadFinished(List<Data> data, Source source) {
                        // your code here, for example:
                        Log.e(TAG, "onLoadFinished(): " + data.get(0).getTitle());

                        countDownLatch.countDown();
                    }

                    @Override
                    public void onLoadError(Throwable throwable, Source source) {
                        // your code here

                        countDownLatch.countDown();
                    }
                };
/*
        ////////
        // normally it should be enough - but here we have the local client, so see below...

        Retrofit2Loader.start("https://...", Retrofit2Api.class, Retrofit2Api::getData,
                null, loaderCallbacks, null, this);

        ////////
*/
        Retrofit2<Retrofit2Api, List<Data>> retrofit2 = new Retrofit2<>();

        Retrofit2Loader.start("http://localhost/", Retrofit2Api.class, Retrofit2Api::getData,

                null,
/* or           new akha.yakhont.technology.rx.BaseRx.SubscriberRx<List<Data>>() {
                    @Override
                    public void onNext(List<Data> data) {
                        // your code here, for example:
                        Log.e("yakhont", "Rx3.onNext(): " + data.get(0).getTitle());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // your code here
                    }
                },
*/
                loaderCallbacks, null, this,
                new LocalOkHttpClient2(retrofit2) /* .setEmulatedNetworkDelay(3) */ , retrofit2);

        // prevents service destroying before data receiving
        Utils.await(countDownLatch);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // for Yakhont Weaver demo

    @SuppressWarnings("unused")
    public static void demoWeaving(String msg, String cls, String method) {
        Log.e(TAG, msg + "class: " + cls + ", method: " + method);
    }

    private void demoWeaving() {

        CoreLogger.setShowAppId(false);     // just for aar weaving demo

        DemoWeaving.demoStatic();
        DemoWeaving demoWeaving = new DemoWeaving();
        demoWeaving.demo();

        DemoWeaving.DemoInner demoInner = demoWeaving.new DemoInner();
        demoInner.demoInner1();
        demoInner.demoInner2();

        // new methods (created by the Yakhont Weaver)
        try {
            //noinspection JavaReflectionMemberAccess
            DemoWeaving.DemoInner.class.getMethod("x").invoke(demoInner);
            //noinspection JavaReflectionMemberAccess
            DemoWeaving.DemoInner.class.getMethod("y").invoke(demoInner);
            //noinspection JavaReflectionMemberAccess
            DemoWeaving          .class.getMethod("z",
                    String.class, int.class).invoke(null, "", 0);
        }
        catch (Exception exception) {       // should never happen
            Log.e(TAG, "new method invocation error", exception);
        }
    }

    private static class DemoWeaving {

        @SuppressWarnings("EmptyMethod")
        private static void demoStatic() {}
        @SuppressWarnings("EmptyMethod")
        private        void demo      () {}

        @SuppressWarnings("InnerClassMayBeStatic")
        private class DemoInner {

            @SuppressWarnings("EmptyMethod")
            private void demoInner1() {}
            @SuppressWarnings("EmptyMethod")
                    void demoInner2() {}
        }
    }
}
