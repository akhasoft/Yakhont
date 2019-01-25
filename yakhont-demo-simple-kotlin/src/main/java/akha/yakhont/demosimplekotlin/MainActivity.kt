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
import akha.yakhont.demosimplekotlin.retrofit.LocalJsonClient2
import akha.yakhont.demosimplekotlin.retrofit.Retrofit2Api

import akha.yakhont.Core
import akha.yakhont.CorePermissions
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

    override fun onCreate(savedInstanceState: Bundle?) {

        Core.init(application, true, null)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        (findViewById<View>(R.id.recycler) as RecyclerView)
                .layoutManager = LinearLayoutManager(this)
/*
        ////////
        // normally it should be enough - but here we have the local client; so see below...

        Retrofit2Loader.load<Retrofit2Api>("http://...", Retrofit2Api::class.java, { it.data }, BR.beer)

        ////////
*/
        val retrofit2 = Retrofit2<Retrofit2Api, Array<Beer>>()
        Retrofit2Loader.get("http://localhost/", Retrofit2Api::class.java, { it.data }, BR.beer,
                LocalJsonClient2(retrofit2), retrofit2).load()
    }

    override fun onLocationChanged(location: Location, date: Date) {
        (findViewById<View>(R.id.location) as TextView).text = LocationCallbacks.toDms(location, this)
    }

    // for Location
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        CorePermissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
