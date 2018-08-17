/*
 * Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demosimple;

import akha.yakhont.demosimple.model.Beer;
import akha.yakhont.demosimple.retrofit.LocalJsonClient2;
import akha.yakhont.demosimple.retrofit.Retrofit2Api;

import akha.yakhont.technology.retrofit.Retrofit2;

import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;

// use import below for support Fragments (android.support.v4.app.Fragment etc.)
// import akha.yakhont.support.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;

// also, don't forget to change in build.gradle 'yakhont' to 'yakhont-support' (or 'yakhont-full')

import android.app.Fragment;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MainFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        MainActivity activity = (MainActivity) getActivity();

        ((RecyclerView) getActivity().findViewById(R.id.recycler))
                .setLayoutManager(new LinearLayoutManager(activity));

        new Retrofit2CoreLoadBuilder<>(this, createRetrofit())
                .setRequester(Retrofit2Api::getData)
                .create()

                // uncomment to stay in application if user cancelled data loading
//              .setGoBackOnLoadingCanceled(false)

                .load();
    }

    // every loader should have unique Retrofit2 object; don't share it with other loaders
    private Retrofit2<Retrofit2Api, Beer[]> createRetrofit() {
        final Retrofit2<Retrofit2Api, Beer[]> retrofit2 = new Retrofit2<>();

        // local JSON client, so URL doesn't matter
        // uncomment network delay emulation for the progress dialog etc.
        retrofit2.init(Retrofit2Api.class, retrofit2.getDefaultBuilder("http://localhost/")
                .client(new LocalJsonClient2(retrofit2) /* .setEmulatedNetworkDelay(10) */ ));

        // for normal HTTP requests you can do something like this
//      return new Retrofit2<Retrofit2Api, Beer[]>().init(Retrofit2Api.class, "http://...");

        return retrofit2;
    }
}
