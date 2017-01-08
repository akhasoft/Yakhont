# copied from https://github.com/krschultz/android-proguard-snippets

-keep public class android.support.v7.widget.** { *; }
-keep public class android.support.v7.internal.widget.** { *; }
-keep public class android.support.v7.internal.view.menu.** { *; }

-keep public class * extends android.support.v4.view.ActionProvider {
    public <init>(android.content.Context);
}

# added by akha
-dontnote android.support.v7.app.ActionBar$LayoutParams
-dontnote android.support.v7.app.AppCompatDialog
-dontnote android.support.v7.view.menu.ActionMenuItemView
-dontnote android.support.v7.view.menu.MenuBuilder
-dontnote android.support.v7.view.menu.SubMenuBuilder
-dontnote android.support.v7.widget.**
