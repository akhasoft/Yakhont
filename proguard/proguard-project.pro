#
#  Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Reduce the size of the output some more.

-repackageclasses

# Keep a fixed source file attribute and all line number tables to get line numbers in the stack traces.
# You can comment this out if you're not interested in stack traces.

-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

################
# for demo

-dontwarn akha.yakhont.adapter.BaseRecyclerViewAdapter$PagingRecyclerViewAdapter
-dontwarn akha.yakhont.loader.BaseViewModel$PagingViewModel

-dontwarn akha.yakhont.loader.BaseLiveData$LiveDataDialog
-dontwarn akha.yakhont.R$id

-dontwarn okhttp3.logging.HttpLoggingInterceptor
-dontwarn okhttp3.logging.HttpLoggingInterceptor$Level

-keep class okhttp3.internal.http2.ErrorCode

################

################
# for demo simple

-dontwarn io.reactivex.**
-dontwarn okhttp3.logging.**

-dontnote rx.schedulers.**

-dontwarn retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
-dontwarn retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

################
