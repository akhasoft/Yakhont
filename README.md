# Yakhont: high-level Android components for data loading, location and lifecycle callbacks

Yakhont is an Android high-level library offering developer-defined callbacks,
loader wrappers, cache, location-awareness, lifecycle debug classes, advanced logging and more.

It extends the [Application.ActivityLifecycleCallbacks](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html)
approach to support your own callbacks creation, that allows to customize handling of
almost every lifecycle state of your Activities and Fragments - and even without changing
their sources (especially useful for libraries developers).

Powerful loader wrappers, which allows loading data in just one line of code, are abstracting
you away from things like loaders management, data binding and caching, progress dialogs
(fully customizable), errors handling and low-level threading;
don't miss the swipe refresh and Rx support too.

Other features includes:
- out-of-the-box location awareness: just annotate your Activity and you're done
- self-configurable transparent cache which adjusts database structure 'on the fly'
- debug classes with strict mode and lifecycle logging - for all kinds of
Activities and Fragments (can be enabled even for 3rd-party components)
- advanced logging with e-mail support (auto-disabled in release builds) and more.

All Activities and Fragments are supported: it's not necessary to derive them from any predefined
ones (with one exception - you will need it for lifecycle debug).

The Yakhont AAR is about 200 KB (except the _full_ version, which is 400 KB).

Yakhont supports Android 2.3 and above (_core_ version requires Android 3.0 as a minimum).

## Demo

Demo application is available [here](https://akhasoft.github.io/yakhont/yakhont-demo.apk).

## Versions

- _core_: works with native Fragments
- _support_: works with support Fragments (android.support.v4.app.Fragment etc.)
- _full_: core + support + debug classes for Activities and Fragments

## Usage

Add the following to your build.gradle:

```groovy
buildscript {
    dependencies {
        classpath 'akha.yakhont.weaver:yakhont-weaver:0.9.17'
        classpath 'org.javassist:javassist:3.20.0-GA'
    }
}

dependencies {
    compile 'com.github.akhasoft:yakhont:0.9.17'
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
$ ./gradlew -x yakhont-weaver:clean clean build
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
