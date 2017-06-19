# initially copied from https://github.com/krschultz/android-proguard-snippets,
#
# then updated from https://developers.google.com/android/guides/setup#add_google_play_services_to_your_project
#
# and finally, rejected it all and...

# added by akha

-dontnote com.google.android.gms.common.internal.safeparcel.SafeParcelable
# added for SDK version 25
-dontnote com.google.android.gms.common.internal.ReflectedParcelable
-dontnote com.google.android.gms.gcm.GcmListenerService

# for yakhont-demo
-dontnote com.google.android.gms.internal.**
-dontnote com.google.android.gms.flags.impl.FlagProviderImpl

-keepclassmembers class com.google.android.gms.dynamite.descriptors.com.google.android.gms.flags.ModuleDescriptor { *; }
-dontnote com.google.android.gms.dynamite.DynamiteModule

# added for library version 11.0.0
-dontnote com.google.protobuf.ExtensionRegistry
-dontnote com.google.protobuf.Extension
-dontnote libcore.io.Memory
-dontnote org.robolectric.Robolectric
