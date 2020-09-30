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

package akha.yakhont.demosimplekotlin

import akha.yakhont.demosimplekotlin.model.Beer
import akha.yakhont.demosimplekotlin.retrofit.LocalOkHttpClient2
import akha.yakhont.demosimplekotlin.retrofit.Retrofit2Api

import kotlinx.android.synthetic.main.activity_main.locationView
import kotlinx.android.synthetic.main.activity_main.recyclerView

import akha.yakhont.Core
import akha.yakhont.Core.Utils
import akha.yakhont.CoreLogger
import akha.yakhont.adapter.BaseRecyclerViewAdapter.OnItemClickListener
import akha.yakhont.callback.annotation.CallbacksInherited
import akha.yakhont.location.LocationCallbacks
import akha.yakhont.location.LocationCallbacks.LocationListener
import akha.yakhont.technology.retrofit.Retrofit2
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader
import akha.yakhont.technology.rx.BaseRx.SubscriberRx

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager

import java.util.Date

// if you have more than one loader in your Activity / Fragment / Service -
//   please provide unique ViewModel keys
//private const val DEMO_VIEWMODEL_KEY = "yakhont_demo_simple_viewmodel_key"

// for Yakhont Weaver demo
fun demoWeaving1(msg: String, cls: String, method: String) {
    Log.e("yakhont", msg + "class: " + cls + ", method: " + method)
}

@CallbacksInherited(LocationCallbacks::class)
class MainActivity: AppCompatActivity(), LocationListener {

    // for Yakhont Weaver demo
    object DemoWeaving {
        @JvmStatic
        fun demoWeaving2(msg: String, cls: String, method: String) {
            demoWeaving1(msg, cls, method)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // uncomment if you're going to use Rx; for more info please refer to
        //   https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
//      Core.setRxUncaughtExceptionBehavior(false)      // not terminate

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) Utils.showToast(R.string.demo_description, 7)

        setDebugLogging(BuildConfig.DEBUG)      // optional

        setLocation()

        recyclerView.layoutManager = LinearLayoutManager(this)
/*
        ////////
        // normally it should be enough - but here we have the local client, so see below...

        Retrofit2Loader.start<Retrofit2Api>("https://...", Retrofit2Api::class.java, {it.data},
                BR.beer, savedInstanceState)

        ////////
*/
        if (savedInstanceState != null)                  // handling screen orientation changes
            Retrofit2Loader.getExistingLoader<Throwable, Array<Beer>>(
                null, this, getListItemClickHandler(), getRx(), null, null)
// or:      Retrofit2Loader.getExistingLoader<Throwable, Array<Beer>>(DEMO_VIEWMODEL_KEY, this, ...)
        else {
            val retrofit2 = Retrofit2<Retrofit2Api, Array<Beer>>()

            Retrofit2Loader.get("http://localhost/", Retrofit2Api::class.java, { it.data },
                null /*DEMO_VIEWMODEL_KEY*/, BR.beer, LocalOkHttpClient2(retrofit2)

                    // just to demo the progress GUI - comment it out if not needed
                    .setEmulatedNetworkDelay(12)

                , retrofit2, getRx(), null, { view, _ -> handleItemClick(view) }, null).start()
        }

        CoreLogger.setShowAppId(false)     // just for aar weaving demo
    }

    private fun handleItemClick(view: View) {
        // your code here, for example:
        Toast.makeText(this@MainActivity, (view.findViewById<View>(R.id.title) as TextView).text,
            Toast.LENGTH_SHORT).show()
    }

    private fun getListItemClickHandler(): OnItemClickListener {
        return OnItemClickListener { view: View, _: Int -> handleItemClick(view) }
    }

    private fun getRx(): SubscriberRx<Array<Beer>>? {
        return null /* object: SubscriberRx<Array<Beer>>() {
            override fun onNext(data: Array<Beer>?) {
                // your code here
            }

            override fun onError(throwable: Throwable) {
                // your code here
            }
        } */
    }

    private fun setDebugLogging(debug: Boolean) {
        if (debug) Core.setFullLoggingInfo(true)

        // optional; on shaking device (or make Z-gesture) email with logs will be sent to the address below
        CoreLogger.registerDataSender(this, "address@company.com")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // location handling

    companion object {
        private var location: String? = null

        // for Yakhont Weaver demo
        @JvmStatic
        fun demoWeaving3(msg: String, cls: String, method: String) {
            demoWeaving1(msg, cls, method)
        }
    }

    override fun onLocationChanged(newLocation: Location, newDate: Date) {
        location = LocationCallbacks.toDms(newLocation, this)
        setLocation()
    }

    private fun setLocation() {
        if (location != null) locationView.text = location
    }
}
