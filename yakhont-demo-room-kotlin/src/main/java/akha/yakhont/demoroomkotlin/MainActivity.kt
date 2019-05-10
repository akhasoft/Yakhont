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

package akha.yakhont.demoroomkotlin

import akha.yakhont.demoroomkotlin.model.Beer
import akha.yakhont.demoroomkotlin.retrofit.LocalOkHttpClient2
import akha.yakhont.demoroomkotlin.retrofit.Retrofit2Api

import akha.yakhont.callback.annotation.CallbacksInherited
import akha.yakhont.location.LocationCallbacks
import akha.yakhont.location.LocationCallbacks.LocationListener
import akha.yakhont.technology.retrofit.Retrofit2
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader

import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import java.util.Date

@CallbacksInherited(LocationCallbacks::class)
class MainActivity: AppCompatActivity(), LocationListener {

    companion object {
        private var      sLocation: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//      akha.yakhont.Core.setFullLoggingInfo(true)      // for debug

        setLocation()

        (findViewById<View>(R.id.recycler) as RecyclerView)
                .layoutManager = LinearLayoutManager(this)
/*
        ////////
        // normally it should be enough - but here we have the local client, so see below...

        Retrofit2Loader.start<Retrofit2Api>("http://...", Retrofit2Api::class.java, { it.data },
                BR.beer, savedInstanceState)

        ////////
*/
        val retrofit2 = Retrofit2<Retrofit2Api, Array<Beer>>()

        Retrofit2Loader.get("http://localhost/", Retrofit2Api::class.java, { it.data }, BR.beer,
                LocalOkHttpClient2(retrofit2), retrofit2, savedInstanceState).start(savedInstanceState)
    }

    override fun onLocationChanged(location: Location, date: Date) {
        sLocation = LocationCallbacks.toDms(location, this)
        setLocation()
    }

    private fun setLocation() {
        if (sLocation != null) (findViewById<View>(R.id.location) as TextView).text = sLocation
    }
}
