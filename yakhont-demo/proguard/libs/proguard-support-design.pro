# copied from https://github.com/krschultz/android-proguard-snippets

-dontwarn android.support.design.**
-keep class android.support.design.** { *; }
-keep interface android.support.design.** { *; }
-keep public class android.support.design.R$* { *; }

# added by akha
-dontnote android.support.design.internal.NavigationMenuItemView
-dontnote android.support.design.internal.NavigationMenuPresenter
-dontnote android.support.design.internal.NavigationMenuPresenter$NavigationMenuAdapter
-dontnote android.support.design.internal.NavigationMenuPresenter$NavigationMenuTextItem
-dontnote android.support.design.internal.NavigationSubMenu
