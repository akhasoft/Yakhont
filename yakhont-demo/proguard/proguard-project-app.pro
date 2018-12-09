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

-keep class akha.yakhont.demo.BuildConfig { *; }

-keep class akha.yakhont.demo.model.** { *; }

-keep interface akha.yakhont.demo.retrofit.** { *; }


-allowaccessmodification

-keepattributes InnerClasses

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
-keepclassmembers class android.support.v4.graphics.TypefaceCompatApi26Impl {
    *** abortCreation(...);
}
-keepclassmembers class android.support.v4.text.ICUCompat {
    *** addLikelySubtags(...);
}
-keepclassmembers class android.support.transition.GhostView** {
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

-keep class android.support.v4.app.Fragment
-keep class android.support.v4.app.FragmentManager
-keep class android.support.v4.app.FragmentTransaction
-keep class android.support.v4.app.Fragment$SavedState
-keep class android.support.v4.app.Fragment$OnStartEnterTransitionListener
-keep class android.support.v4.app.SharedElementCallback
-keep class android.support.v4.graphics.PathParser$PathDataNode
-keep class android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
-keep class android.support.v4.view.AbsSavedState$1
-keep class android.support.v4.view.ActionProvider$SubUiVisibilityListener
-keep class android.support.v4.view.ActionProvider$VisibilityListener
-keep class android.support.v4.view.PagerAdapter
-keep class android.support.v4.view.ViewPager
-keep class android.support.v4.view.WindowInsetsCompat
-keep class android.support.v7.view.menu.MenuItemImpl
-keep class android.support.v7.view.menu.MenuPresenter$Callback
-keep class android.support.v4.util.ArrayMap
-keep class android.support.v4.util.LongSparseArray

-keep class android.support.transition.PathMotion
-keep class android.support.transition.Transition
-keep class android.support.transition.TransitionPropagation
-keep class android.support.transition.TransitionValues
-keep class android.support.transition.TransitionValuesMaps
-keep class android.support.transition.Transition$TransitionListener
-keep class android.support.transition.Transition$EpicenterCallback

-keep class okhttp3.internal.http2.ErrorCode

-keep class com.google.android.gms.common.images.ImageManager$OnImageLoadedListener
-keep class com.google.android.gms.common.images.internal.LoadingImageView$ClipPathProvider

-dontwarn akha.yakhont.loader.BaseLiveData$LiveDataDialog
