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

package akha.yakhont.demosimplekotlin

import akha.yakhont.demosimplekotlin.model.Beer
import akha.yakhont.demosimplekotlin.retrofit.LocalOkHttpClient2
import akha.yakhont.demosimplekotlin.retrofit.Retrofit2Api

import kotlinx.android.synthetic.main.activity_main.locationView
import kotlinx.android.synthetic.main.activity_main.recyclerView

import akha.yakhont.callback.annotation.CallbacksInherited
import akha.yakhont.location.LocationCallbacks
import akha.yakhont.location.LocationCallbacks.LocationListener
import akha.yakhont.technology.retrofit.Retrofit2
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader
import akha.yakhont.technology.rx.BaseRx.SubscriberRx

import android.location.Location
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager

import java.util.Date

// if you have more than one loader in your Activity / Fragment / Service -
//   please provide unique ViewModel keys
//private const val DEMO_VIEWMODEL_KEY = "yakhont_demo_simple_viewmodel_key"

@CallbacksInherited(LocationCallbacks::class)
class MainActivity: AppCompatActivity(), LocationListener {

    override fun onCreate(savedInstanceState: Bundle?) {

        // uncomment if you're going to use Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      akha.yakhont.Core.setRxUncaughtExceptionBehavior(false)      // not terminate

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//      akha.yakhont.Core.setFullLoggingInfo(true)      // for debug

        setLocation()

        recyclerView.layoutManager = LinearLayoutManager(this)
/*
        ////////
        // normally it should be enough - but here we have the local client, so see below...

        Retrofit2Loader.start<Retrofit2Api>("http://...", Retrofit2Api::class.java, {it.data},
                BR.beer, savedInstanceState)

        ////////
*/
        if (savedInstanceState != null)                  // handling screen orientation changes
            Retrofit2Loader.getExistingLoader<Throwable, Array<Beer>>()
//          Retrofit2Loader.getExistingLoader<Throwable, Array<Beer>>(DEMO_VIEWMODEL_KEY, this)
        else {
            val rx: SubscriberRx<Array<Beer>>? = null
/* or       val rx = object: SubscriberRx<Array<Beer>>() {
                override fun onNext(data: Array<Beer>?) {
                    // your code here
                }

                override fun onError(throwable: Throwable) {
                    // your code here
                }
            }
*/
            val retrofit2 = Retrofit2<Retrofit2Api, Array<Beer>>()

            Retrofit2Loader.get("http://localhost/", Retrofit2Api::class.java, {it.data},
                null /*DEMO_VIEWMODEL_KEY*/, BR.beer, LocalOkHttpClient2(retrofit2)

                    // just to demo the progress GUI - comment it out if not needed
                    .setEmulatedNetworkDelay(7)

                    , retrofit2, rx, null, null).start()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // location handling

    companion object {
        private var location: String? = null
    }

    override fun onLocationChanged(newLocation: Location, newDate: Date) {
        location = LocationCallbacks.toDms(newLocation, this)
        setLocation()
    }

    private fun setLocation() {
        if (location != null) locationView.text = location
    }
}
