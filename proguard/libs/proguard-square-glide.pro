# copied from https://github.com/bumptech/glide

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# commented out by akha
# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
