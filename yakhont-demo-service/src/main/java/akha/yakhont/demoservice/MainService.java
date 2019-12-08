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

package akha.yakhont.demoservice;

import akha.yakhont.demoservice.model.Data;
import akha.yakhont.demoservice.retrofit.LocalOkHttpClient2;
import akha.yakhont.demoservice.retrofit.Retrofit2Api;

import akha.yakhont.Core.Utils;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallbacks;
import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import java.util.List;
import java.util.concurrent.CountDownLatch;

//@CallbacksInherited(LocationCallbacks.class) //todo
public class MainService extends IntentService implements ViewModelStoreOwner {

    private final ViewModelStore mViewModelStore = new ViewModelStore();

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return mViewModelStore;
    }

    public MainService() {
        super("MainService");
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onCreate() {
        // uncomment if you're going to use Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      akha.yakhont.Core.setRxUncaughtExceptionBehavior(false);    // not terminate

        super.onCreate();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Retrofit2<Retrofit2Api, List<Data>> retrofit2 = new Retrofit2<>();

        Retrofit2Loader.start("http://localhost/", Retrofit2Api.class, Retrofit2Api::getData,

                null,
/* or           new akha.yakhont.technology.rx.BaseRx.SubscriberRx<List<Data>>() {
                    @Override
                    public void onNext(List<Data> data) {
                        // your code here, for example:
                        Log.e("yakhont", "Rx2.onNext(): " + data.get(0).getTitle());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // your code here
                    }
                },
*/
                new LoaderCallbacks<Throwable, List<Data>>() {
                    @Override
                    public void onLoadFinished(List<Data> data, Source source) {
                        // your code here, for example:
                        Log.e("yakhont", "onLoadFinished(): " + data.get(0).getTitle());

                        countDownLatch.countDown();
                    }

                    @Override
                    public void onLoadError(Throwable throwable, Source source) {
                        // your code here

                        countDownLatch.countDown();
                    }
                },

                null, this,
                new LocalOkHttpClient2(retrofit2) /* .setEmulatedNetworkDelay(7) */ , retrofit2);

        // prevents service destroying before receiving data
        Utils.await(countDownLatch, 0);
    }
}
