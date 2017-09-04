<div align="center">
    <table><tr>
        <td width="42%" align="left">Яхонт делает невозможное</td>
        <td><img src="https://akhasoft.github.io/yakhont/library/core/logo.png"></td>
        <td width="42%" align="right">Yakhont - break limits now</td>
    </tr></table>
</div>         

# Yakhont: high-level Android components for data loading, location, lifecycle callbacks and more

Yakhont is an Android high-level library offering developer-defined callbacks,
loader wrappers, cache, location-awareness, permissions handling, lifecycle debug classes, 
advanced logging and many more helpful developer-oriented issues.

It extends the [Application.ActivityLifecycleCallbacks](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html)
approach to support your own callbacks creation, that allows to customize handling of
almost every lifecycle state of your Activities and Fragments - and even without changing
their sources (especially useful for libraries developers).

Powerful loader wrappers, which allows loading data in just one line of code, are abstracting
you away from things like loaders management, data binding and caching, progress dialogs
(fully customizable), errors handling and low-level threading;
don't miss the swipe refresh and Rx support too.

In short, the data loader features are:
- fully asynchronous 
- forced timeout 
- transparent cache
- both [RxJava](https://github.com/ReactiveX/RxJava/tree/1.x) and [RxJava 2](https://github.com/ReactiveX/RxJava) support
- both [Retrofit](http://square.github.io/retrofit/1.x/retrofit/) and [Retrofit 2](http://square.github.io/retrofit/2.x/retrofit/) support
- swipe-to-refresh
- device orientation changing support
- fully customizable GUI progress (via Dagger 2) 

Other features includes:
- out-of-the-box location awareness: just annotate your Activity and you're done
- self-configurable transparent cache which adjusts database structure 'on the fly'
- debug classes with strict mode and lifecycle logging - for all kinds of
Activities and Fragments (can be enabled even for 3rd-party components)
- advanced logging with e-mail support (auto-disabled in release builds) and more.

All Activities and Fragments are supported: it's not necessary to derive them from any predefined
ones (with one exception - you will need it for lifecycle debug).

The Yakhont AAR is about 300 KB (except the _full_ version, which is about 500 KB).

Yakhont supports Android 2.3 (API level 9) and above
(_core_ version requires Android 3.0 (API level 11) as a minimum).

**Note:** Location API requires Android 4.0 (API level 14).

## Demo

Demo applications are available [here](https://akhasoft.github.io/yakhont/yakhont-demo.apk) and
[here (simplified version)](https://akhasoft.github.io/yakhont/yakhont-demo-simple.apk).

## Versions

- _core_: works with native Fragments
- _support_: works with support Fragments (android.support.v4.app.Fragment etc.)
- _full_: core + support + debug classes for Activities and Fragments

## Usage

Add the following to your build.gradle:

```groovy
buildscript {
    dependencies {
        classpath 'akha.yakhont.weaver:yakhont-weaver:0.9.18'
        classpath 'org.javassist:javassist:3.20.0-GA'
    }
}

dependencies {
    compile 'com.github.akhasoft:yakhont:0.9.18'
}

String[] weaverConfigFiles = null
boolean weaverDebug = false, weaverAddConfig = true
android.registerTransform(new akha.yakhont.weaver.WeaverTransform(weaverDebug, android.defaultConfig.applicationId,
    android.bootClasspath.join(File.pathSeparator), weaverConfigFiles, weaverAddConfig))
```
   
To avoid using the Transform API, you can try the following:

```groovy
android.applicationVariants.all { variant ->
    JavaCompile javaCompile = variant.javaCompile
    javaCompile.doLast {
        new akha.yakhont.weaver.Weaver().run(weaverDebug, android.defaultConfig.applicationId,
            javaCompile.destinationDir.toString(), javaCompile.classpath.asPath,
            android.bootClasspath.join(File.pathSeparator), weaverConfigFiles, weaverAddConfig)
    }
}
```

Here the Yakhont weaver manipulates the Java bytecode just compiled, which makes possible
to alternate classes implementation (e.g. add callbacks to Activities and Fragments)
without changing their source code.

**Note:** the new Google "Jack and Jill" technology is not supporting bytecode manipulation
(at least, for the moment).

## Weaver configuration

By default the Yakhont weaver uses configuration file from it's JAR, but you can specify your own
configuration(s) as a parameter (see above). The "weaverAddConfig = true" means adding your
configuration (if not null) to the default one; "weaverAddConfig = false" forces the Yakhont weaver
to replace default configuration with yours (even if null).

## Proguard

ProGuard directives are included in the Yakhont libraries. The Android Plugin for Gradle
automatically appends these directives to your ProGuard configuration.

Anyway, it's strongly advised also to keep your model and Retrofit API as follows
(it's just an example from the Yakhont Demo, 
please update it according to your application's packages names): 

```
-keep class akha.yakhont.demo.model.** { *; }
-keep interface akha.yakhont.demo.retrofit.** { *; }
```

## Build

To check out and build the Yakhont source, issue the following commands:

```
$ git clone git@github.com:akhasoft/Yakhont.git
$ cd Yakhont
$ ./gradlew build
```

To do a clean build, issue the following commands:

```
$ ./gradlew --configure-on-demand yakhont-weaver:clean yakhont-weaver:build
$ ./gradlew --configure-on-demand yakhont:clean yakhont:build
$ ./gradlew --configure-on-demand yakhont-demo:clean yakhont-demo:build
$ ./gradlew --configure-on-demand yakhont-demo-simple:clean yakhont-demo-simple:build
```

**Note:** you may need to update your Android SDK before building.

To avoid some lint issues (in Android Studio, when running Analyze -> Inspect Code):

- add **yakhont.link,yakhont.see** to File -> Settings -> Editor -> Inspections -> Java -> Javadoc issues -> Declaration has Javadoc problems -> Additional Javadoc Tags
- add **yakhont.dic** to File -> Settings -> Editor -> Spelling -> Dictionaries

## Communication

- [GitHub Issues](https://github.com/akhasoft/Yakhont/issues)

## Documentation

- [Javadoc - core](https://akhasoft.github.io/yakhont/library/core/)
- [Javadoc - full](https://akhasoft.github.io/yakhont/library/full/)
- [Javadoc - support](https://akhasoft.github.io/yakhont/library/support/)
- [Javadoc - weaver](https://akhasoft.github.io/yakhont/weaver/)
- [Wiki](https://github.com/akhasoft/Yakhont/wiki)

## Known Issues

Not yet.

## Bugs and Feedback

For bugs, questions and discussions please use the
[Github Issues](https://github.com/akhasoft/Yakhont/issues).

## LICENSE

    Copyright (C) 2015-2017 akha, a.k.a. Alexander Kharitonov
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
