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

package akha.yakhont.demosimple.retrofit;

import akha.yakhont.demosimple.model.Data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface Retrofit2Api {

    @SuppressWarnings("UnusedReturnValue")
    @GET("/data")
    Call<List<Data>> getData( /* @retrofit2.http.Query("parameter") String parameter */ );
}
