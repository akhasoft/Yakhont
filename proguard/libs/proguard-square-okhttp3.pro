# initially copied from https://github.com/square/okhttp

# now from OkHttp GitHub

# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*


# added by akha
# for yakhont-demo
-dontnote dalvik.system.CloseGuard
-dontnote com.android.org.conscrypt.SSLParametersImpl
-dontnote com.android.org.conscrypt.OpenSSLSocketImpl
-dontnote org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontnote org.conscrypt.Conscrypt
-dontnote org.conscrypt.ConscryptEngineSocket
-dontnote sun.security.ssl.SSLContextImpl
