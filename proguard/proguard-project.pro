#
#  Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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

-repackageclasses ''

# Keep a fixed source file attribute and all line number tables to get line numbers in the stack traces.
# You can comment this out if you're not interested in stack traces.

-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

# Preserve all fundamental application classes.

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keep class android.support.v4.** { *; }
-keep interface android.support.v4.** { *; }

-dontnote android.support.v4.**

-dontnote android.net.http.SslCertificate
-dontnote android.net.http.SslCertificate$DName
-dontnote android.net.http.SslError
# added for SDK version 24
-dontnote android.net.http.HttpResponseCache

-dontnote com.android.vending.licensing.ILicensingService
-dontnote com.google.vending.licensing.ILicensingService

-dontnote org.apache.http.conn.ConnectTimeoutException
-dontnote org.apache.http.conn.scheme.HostNameResolver
-dontnote org.apache.http.conn.scheme.SocketFactory
-dontnote org.apache.http.params.HttpParams
# added for SDK version 24
-dontnote org.apache.http.conn.scheme.LayeredSocketFactory
-dontnote org.apache.http.params.CoreConnectionPNames
-dontnote org.apache.http.params.HttpConnectionParams

-dontnote android.support.v7.preference.Preference
-dontnote android.support.v17.leanback.Scale

# for yakhont-demo

-dontwarn retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
-dontwarn retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

-dontnote android.support.v17.leanback.widget.VerticalGridView
# added for SDK version 24
-dontnote android.support.v7.app.MediaRouteButton
-dontnote android.support.v17.leanback.widget.BaseGridView
-dontnote android.support.v17.leanback.widget.BrowseFrameLayout
-dontnote android.support.v17.leanback.widget.GuidedActionEditText
-dontnote android.support.v17.leanback.widget.HorizontalGridView
-dontnote android.support.v17.leanback.widget.SearchBar
-dontnote android.support.v17.leanback.widget.SearchEditText
-dontnote android.support.v17.leanback.widget.SearchOrbView
-dontnote android.support.v17.leanback.widget.TitleView
