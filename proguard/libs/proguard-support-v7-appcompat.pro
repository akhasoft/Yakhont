-keep public class androidx.appcompat.widget.** { *; }
-keep public class androidx.preference.internal.** { *; }

-keep public class * extends androidx.core.view.ActionProvider {
    public <init>(android.content.Context);
}
