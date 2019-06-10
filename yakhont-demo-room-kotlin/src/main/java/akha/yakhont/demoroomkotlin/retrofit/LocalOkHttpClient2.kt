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

package akha.yakhont.demoroomkotlin.retrofit

import akha.yakhont.technology.retrofit.BaseLocalOkHttpClient2
import akha.yakhont.technology.retrofit.Retrofit2

// OkHttp3 local client to load data from array
class LocalOkHttpClient2(retrofit2: Retrofit2<*, *>): BaseLocalOkHttpClient2(retrofit2) {

    override fun getContent(): String {
        return getJson(
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
                "Wilderen Goud"
        )
    }
}