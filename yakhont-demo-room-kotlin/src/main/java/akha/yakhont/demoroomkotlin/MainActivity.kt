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
import akha.yakhont.demoroomkotlin.room.AppDatabase
import akha.yakhont.demoroomkotlin.room.Item

import kotlinx.android.synthetic.main.recycler_item.view.title

import akha.yakhont.Core
import akha.yakhont.Core.Utils
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter
import akha.yakhont.callback.annotation.CallbacksInherited
import akha.yakhont.loader.BaseConverter
import akha.yakhont.loader.BaseResponse
import akha.yakhont.loader.BaseViewModel
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters
import akha.yakhont.location.LocationCallbacks
import akha.yakhont.location.LocationCallbacks.LocationListener
import akha.yakhont.technology.retrofit.Retrofit2
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2Loader

import android.content.ContentValues
import android.database.Cursor
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room

import java.util.Date
import java.lang.reflect.Type

import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import retrofit2.Response

private const val ROOM_DB_KEY           = "room"
private const val ROOM_DB_NAME          = "demo"
private const val ROOM_DB_TABLE_NAME    = "item"
private const val ROOM_DB_COLUMN_NAME   = "title"

@CallbacksInherited(LocationCallbacks::class)
class MainActivity: AppCompatActivity(), LocationListener {

    // By default Yakhont provides the fully transparent cache -
    //   but you can use Room (or whatever) instead

    private lateinit var client : LocalOkHttpClient2
    private lateinit var adapter: CustomAdapter

    // cache cursor; will be closed automatically by Yakhont in ViewModel.onCleared() method
    private          var cursor : Cursor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) Utils.showToastExt(R.layout.info, 7)

//      akha.yakhont.Core.setFullLoggingInfo(true)      // for debug

        setLocation()

        val recyclerView: RecyclerView = findViewById<View>(R.id.recycler) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (savedInstanceState != null) {                // handling screen orientation changes
            Retrofit2Loader.getExistingLoader<Throwable, List<Beer>>()

            recyclerView.adapter = BaseViewModel.getData<Any>(ROOM_DB_KEY) as CustomAdapter
            return
        }

        adapter = CustomAdapter()
        recyclerView.adapter = adapter

        val retrofit2 = Retrofit2<Retrofit2Api, List<Beer>>()
        client = LocalOkHttpClient2(retrofit2)
        retrofit2.init(Retrofit2Api::class.java, "http://localhost/", client)

        Retrofit2Loader.adjust<List<Beer>>(Retrofit2CoreLoadBuilder(retrofit2)
                .setRequester{it.data}
                .setDataBinding(BR.beer)

                // Room-specific settings
                .setTableName(ROOM_DB_TABLE_NAME)
                .setConverter(RoomConverter())

                // custom-adapter-specific (not related to Room - just custom adapter demo)
                .setAdapter(adapter)

                .create()).start(null)

        BaseViewModel.setData(ROOM_DB_KEY, adapter)
    }

    private fun getItem(cursor: Cursor): Beer {
        val beer = Beer()
        beer.title = cursor.getString(cursor.getColumnIndex(ROOM_DB_COLUMN_NAME))
        return beer
    }

    private inner class RoomConverter: BaseConverter<List<Beer>>() {

        override fun isInternalCache(): Boolean {
            return false
        }

        override fun getData(cursor: Cursor, position: Int): Beer {
            cursor.moveToPosition(position)
            return getItem(cursor)
        }

        override fun getValues(type: String, data: ByteArray, cls: Class<*>, pageId: Long): Collection<ContentValues> {
            val json = JSONArray(String(data))
            val result: ArrayList<ContentValues> = ArrayList()

            for (i in 0 until json.length()) {
                val values = ContentValues()
                values.put(ROOM_DB_COLUMN_NAME, json.getJSONObject(i).getString(ROOM_DB_COLUMN_NAME))
                result.add(values)
            }
            return result
        }

        private fun getDb(): AppDatabase {  // Core keeps the one and only instance of the AppDatabase
            var db: AppDatabase? = Core.getSingleton(ROOM_DB_KEY)
            if (db == null) {
                db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, ROOM_DB_NAME).build()
                Core.setSingleton(ROOM_DB_KEY, db)
            }
            return db
        }

        override fun store(data: Collection<ContentValues>): Boolean {
            val itemDao = getDb().itemDao()
            for (values in data)            // for autoincrement id should be 0
                itemDao.insert(Item(0, values.getAsString(ROOM_DB_COLUMN_NAME)))
            return true
        }

        override fun clear(tableName: String): Boolean {
            getDb().itemDao().deleteAll()
            return true
        }

        @Throws(UnsupportedOperationException::class)
        override fun getCursor(tableName: String, parameters: LoadParameters): Cursor? {
            if (cursor == null) cursor = getDb().itemDao().getAll()
            return cursor
        }

        override fun getType(): Type {
            return object: TypeToken<List<Beer>>(){}.type
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // not related to Room - just custom adapter demo

    private inner class CustomAdapter: RecyclerView.Adapter<CustomAdapter.ViewHolder>(),
            CacheAdapter<Response<List<Beer>>, Throwable, List<Beer>> {

        private val list: ArrayList<Beer> = ArrayList()

        // Yakhont-specific - every custom adapter should implement this
        override fun update(data: BaseResponse<Response<List<Beer>>, Throwable, List<Beer>>,
                            isMerge: Boolean, onLoadFinished: Runnable?) {
            list.clear()
            if (data.error == null)
                list.addAll(data.result)
            else {                              // gets data from cache (in case of loading error)
                data.cursor.moveToFirst()
                while (!data.cursor.isAfterLast) {
                    list.add(getItem(data.cursor))
                    data.cursor.moveToNext()
                }
            }
            adapter.notifyDataSetChanged()
        }

        private inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val title: TextView = view.title
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.recycler_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.title.text = list[position].title
        }

        override fun getItemCount(): Int {
            return list.size
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        private var location: String? = null
    }

    override fun onLocationChanged(location: Location, date: Date) {
        MainActivity.location = LocationCallbacks.toDms(location, this)
        setLocation()
    }

    private fun setLocation() {
        if (location != null) (findViewById<View>(R.id.location) as TextView).text = location
    }
}
