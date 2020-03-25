#
#  Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

-keep class akha.yakhont.BuildConfig { *; }

# Preserve all fundamental application classes.

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# The "Exceptions" attribute has to be preserved, so the compiler knows which exceptions methods may throw.
# The "InnerClasses" attribute (or more precisely, its source name part) has to be preserved too, for any inner classes that can be
#   referenced from outside the library. The javac compiler would be unable to find the inner classes otherwise.
# The "Signature" attribute is required to be able to access generic types when compiling in JDK 5.0 and higher.
# Finally, we're keeping the "Deprecated" attribute and the attributes for producing useful stack traces.

-keepattributes Exceptions, InnerClasses, Signature, Deprecated, EnclosingMethod

# Preserve all public classes, and their public and protected fields and methods.

-keep public class * {
    public protected *;
}
