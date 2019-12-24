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

# Data Binding Library
-dontwarn akha.yakhont.adapter.BaseCacheAdapter$DataBindingViewHolder

-keep class akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed$ActivityLifecycle
-keep class akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed$FragmentLifecycle

-dontnote akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed$FragmentLifecycle

-keep class * extends akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed$BaseActivityCallbacks {
    public void onActivity*(...);
}
-keep class * extends akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed$BaseFragmentCallbacks {
    public void onFragment*(...);
}

-dontnote akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed$BaseFragmentCallbacks

-keep class * extends android.app.Activity {
    *** onActivityResult(...);
}

-keep class * extends androidx.fragment.app.FragmentActivity {
    public void validateRequestPermissionsRequestCode(...);
}

# looks like an API bug, please refer to BaseResponseLoaderWrapper.WrapperLoader
-keep class io.reactivex.internal.** { *; }
-keep class io.reactivex.** { *; }
-dontnote   io.reactivex.**

# for stack traces
-keep class io.reactivex.exceptions.** { *; }
-keep class retrofit2.HttpException { *; }
-keep class retrofit2.adapter.rxjava2.HttpException { *; }

-dontnote retrofit2.HttpException
-dontnote retrofit2.adapter.rxjava2.HttpException

# for FragmentManagerImpl; proguard doesn't allow to specify it directly
-keep class * extends androidx.fragment.app.FragmentManager {
    public void noteStateNotSaved(...);
}

-keep class **.SimpleCursorAdapter { <init>(...); }
-dontnote   **.SimpleCursorAdapter

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *** onCleared(...); }
