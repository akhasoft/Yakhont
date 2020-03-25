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

package akha.yakhont.demo.retrofit;

import akha.yakhont.demo.model.Beer;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface Retrofit2Api {

    @SuppressWarnings("UnusedReturnValue")
    @GET("/data")
    Observable<List<Beer>>              getDataRx   ();  // Flowable, Maybe and Single works too

    @GET("/data")
    @SuppressWarnings("unused")
    io.reactivex.Observable<List<Beer>> getDataRx2  ();  // Flowable, Maybe and Single works too

    @GET("/data")
    @SuppressWarnings("unused")
    rx.Observable<List<Beer>>           getDataRx1  ();  // Single works too

    @GET("/data")
    @SuppressWarnings("unused")
    Call<List<Beer>>                    getData(@Query("not_used") String notUsed /* just for demo */ );
}
