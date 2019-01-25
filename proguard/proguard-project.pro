#
#  Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
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

# looks like an API bug
-dontnote android.databinding.DataBinderMapper

################
# for demo

-dontwarn akha.yakhont.loader.BaseLiveData$LiveDataDialog
-dontwarn akha.yakhont.R$id

-dontwarn okhttp3.logging.HttpLoggingInterceptor
-dontwarn okhttp3.logging.HttpLoggingInterceptor$Level

-dontnote android.content.pm.ParceledListSlice
-dontnote android.content.res.ThemedResourceCache
-dontnote android.graphics.FontFamily
-dontnote android.os.WorkSource$WorkChain
-dontnote android.view.GhostView
-dontnote com.android.internal.view.menu.MenuBuilder
-dontnote libcore.icu.ICU

-keepclassmembers class android.icu.** {
    *** freeze(...);
}
-keepclassmembers class com.google.android.gms.common.data.Freezable {
    *** freeze(...);
}
-keepclassmembers class androidx.core.graphics.TypefaceCompatApi26Impl {
    *** abortCreation(...);
}
-keepclassmembers class androidx.core.text.ICUCompat {
    *** addLikelySubtags(...);
}
-keepclassmembers class androidx.transition.GhostView** {
    *** removeGhost(...);
}
-keepclassmembers class android.security.** {
    *** isCleartextTrafficPermitted(...);
}
-keepclassmembers class okhttp3.** {
    *** isCleartextTrafficPermitted(...);
}
-keepclassmembers class org.conscrypt.** {
    *** findTrustAnchorByIssuerAndSignature(...);
}
-keepclassmembers class dalvik.system.CloseGuard {
    *** warnIfOpen(...);
}
-keepclassmembers class org.eclipse.jetty.alpn.** {
    *** remove(...);
}

-keepclassmembers class android.content.res.AssetManager {
    *** open(...);
}
-keepclassmembers class android.webkit.WebIconDatabase {
    *** open(...);
}

-keepclassmembers class android.** {
    *** close(...);
}
-keepclassmembers class java.** {
    *** close(...);
}
-keepclassmembers class javax.crypto.** {
    *** close(...);
}
-keepclassmembers class javax.sql.** {
    *** close(...);
}
-keepclassmembers class org.apache.** {
    *** close(...);
}
-keepclassmembers class okhttp3.** {
    *** close(...);
}
-keepclassmembers class okio.** {
    *** close(...);
}
-keepclassmembers class retrofit2.OkHttpCall$ExceptionCatchingRequestBody {
    *** close(...);
}
-keepclassmembers class com.google.android.gms.common.** {
    *** close(...);
}
-keepclassmembers class com.google.gson.internal.** {
    *** close(...);
}

-keep class androidx.fragment.app.Fragment
-keep class androidx.fragment.app.FragmentManager
-keep class androidx.fragment.app.FragmentTransaction
-keep class androidx.fragment.app.Fragment$SavedState
-keep class androidx.fragment.app.Fragment$OnStartEnterTransitionListener
-keep class androidx.core.app.SharedElementCallback
-keep class androidx.core.graphics.PathParser$PathDataNode
-keep class androidx.core.view.accessibility.AccessibilityNodeInfoCompat
-keep class androidx.core.view.AbsSavedState$1
-keep class androidx.core.view.ActionProvider$SubUiVisibilityListener
-keep class androidx.core.view.ActionProvider$VisibilityListener
-keep class androidx.core.view.PagerAdapter
-keep class androidx.core.view.ViewPager
-keep class androidx.core.view.WindowInsetsCompat
-keep class androidx.core.view.menu.MenuItemImpl
-keep class androidx.core.view.menu.MenuPresenter$Callback
-keep class androidx.collection.ArrayMap
-keep class androidx.collection.LongSparseArray

-keep class androidx.transition.PathMotion
-keep class androidx.transition.Transition
-keep class androidx.transition.TransitionPropagation
-keep class androidx.transition.TransitionValues
-keep class androidx.transition.TransitionValuesMaps
-keep class androidx.transition.Transition$TransitionListener
-keep class androidx.transition.Transition$EpicenterCallback

-keep class okhttp3.internal.http2.ErrorCode

-keep class com.google.android.gms.common.images.ImageManager$OnImageLoadedListener

################

################
# for demo simple

-dontwarn io.reactivex.**
-dontwarn okhttp3.logging.**

-dontnote android.os.WorkSource$WorkChain
-dontnote rx.schedulers.**

-keep class com.google.android.gms.common.images.ImageManager$OnImageLoadedListener

-dontwarn com.google.android.material.snackbar.**

-dontwarn retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
-dontwarn retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

################

################
# for demo simple kotlin

-dontnote kotlin.internal.jdk8.JDK8PlatformImplementations
-dontnote kotlin.internal.JRE8PlatformImplementations
-dontnote kotlin.internal.JRE7PlatformImplementations
-dontnote kotlin.reflect.jvm.internal.ReflectionFactoryImpl

################
