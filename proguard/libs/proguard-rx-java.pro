# copied from https://github.com/krschultz/android-proguard-snippets

# RxJava 0.21

-keep class rx.schedulers.Schedulers {
    public static <methods>;
}
-keep class rx.schedulers.ImmediateScheduler {
    public <methods>;
}
-keep class rx.schedulers.TestScheduler {
    public <methods>;
}
-keep class rx.schedulers.Schedulers {
    public static ** test();
}

# added by akha

# for Rx3
-dontwarn java.util.concurrent.Flow*

# for yakhont-demo
-dontnote rx.internal.util.PlatformDependent

-keep class rx.functions.Func1 {
    public <methods>;
}
-dontnote rx.functions.Func1
