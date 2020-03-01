# copied from https://github.com/krschultz/android-proguard-snippets

## GSON 2.2.4 specific rules ##

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

-keepattributes EnclosingMethod

# Gson specific classes
-keep class com.google.gson.stream.** { *; }

# added by akha
# -dontnote com.google.gson.internal.UnsafeAllocator
# -dontnote com.google.gson.stream.**
# -dontnote sun.misc.Unsafe
