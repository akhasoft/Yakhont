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

package akha.yakhont.demosimplekotlin.retrofit

import akha.yakhont.technology.retrofit.Retrofit2
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache
import akha.yakhont.technology.retrofit.Retrofit2.BodySaverInterceptor

import okhttp3.logging.HttpLoggingInterceptor

class LocalJsonClient2(private val mRetrofit2: Retrofit2<*, *>): LocalJsonClient2Base() {

    init {
        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BODY
        add(logger)

        add(object: BodySaverInterceptor() {
            override fun set(data: BodyCache) {
                mRetrofit2.data = data
            }
        })
    }

    override fun getJson(): String {
        val builder = StringBuilder("[")
        for (str in DATA)
            builder.append("{\"title\":\"").append(str).append("\"},")
        return builder.replace(builder.length - 1, builder.length, "]").toString()
    }

    companion object {
        private val DATA = arrayOf(
                "Duvel",
                "Abbaye de Brogne",
                "Chimay",
                "Delirium Tremens",
                "Gouden Carolus",
                "Green Killer",
                "Gulden Draak",
                "Liefmans",
                "Orval Trappist",
                "Pauwel Kwak",
                "Petrus",
                "Rodenbach Grand Cru",
                "Val-Dieu",
                "Waterloo",
                "Westmalle",
                "Westvleteren",
                "Wilderen Goud")
    }
}
