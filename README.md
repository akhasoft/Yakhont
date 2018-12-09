<!-- Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->

<div align="center">
    <table><tr>
        <td width="45%" align="left">Яхонт делает невозможное</td>
        <td><img src="https://akhasoft.github.io/yakhont/library/core/logo.png"></td>
        <td width="45%" align="right">Yakhont - break limits now</td>
    </tr></table>
</div>         
        
# Yakhont: high-level Android components for data loading, location, lifecycle callbacks and many more

Yakhont is an Android high-level library offering developer-defined callbacks,
loader wrappers and adapters, fully automatic cache, location-awareness, dynamic permissions handling, 
lifecycle debug classes, advanced logging and many more helpful developer-oriented features.

There is also the Yakhont Weaver - a small but powerful utility which manipulates the compiled Java 
bytecode and can be used separately, without the Yakhont library (you will find more info
[below](https://github.com/akhasoft/Yakhont#weaver-usage-and-configuration)).

Now you can load data in just two lines of code (please refer to the
[simplified demo](yakhont-demo-simple/src/main/java/akha/yakhont/demosimple/MainFragment.java)
for the working example):

```
new Retrofit2CoreLoadBuilder<>(...).setRequester(YourRetrofit::yourMethod)
    .setDataBinding(BR.yourDataBindingID).create().load();
```

And take location in just a couple of lines (the working example is in the
[simplified demo](yakhont-demo-simple/src/main/java/akha/yakhont/demosimple/MainActivity.java)
too):

```
@CallbacksInherited(LocationCallbacks.class)
public class YourActivity extends Activity implements LocationListener {
    @Override
    public void onLocationChanged(Location location, Date date) {
        // your code here
    }
}
```

## Table of Contents

- [Feature List](https://github.com/akhasoft/Yakhont#feature-list)
- [Demo and Releases](https://github.com/akhasoft/Yakhont#demo-and-releases)
- [Versions](https://github.com/akhasoft/Yakhont#versions)
- [Usage](https://github.com/akhasoft/Yakhont#usage)
- [Weaver](https://github.com/akhasoft/Yakhont#weaver-usage-and-configuration)
- [Proguard](https://github.com/akhasoft/Yakhont#proguard)
- [Build](https://github.com/akhasoft/Yakhont#build)
- [Communication](https://github.com/akhasoft/Yakhont#communication)
- [Information and Documentation](https://github.com/akhasoft/Yakhont#information-and-documentation)
- [Known Issues](https://github.com/akhasoft/Yakhont#known-issues)
- [Bugs and Feedback](https://github.com/akhasoft/Yakhont#bugs-and-feedback)
- [License](https://github.com/akhasoft/Yakhont#license)

Yakhont extends the [Application.ActivityLifecycleCallbacks](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html)
approach to support your own callbacks creation, that allows to customize handling of
almost every lifecycle state of your Activities and Fragments - and even without changing
their sources (especially useful for libraries developers).

The powerful loader wrappers and adapters, which (in simplest, but typical case) 
allows loading and binding data in nearly one line of code, are abstracting you away from things like 
loaders management, caching, progress dialogs (fully customizable), errors handling and low-level threading;
don't miss the swipe-to-refresh and both [RxJava](https://github.com/ReactiveX/RxJava/tree/1.x) and
[RxJava 2](https://github.com/ReactiveX/RxJava) support too.

In short, the data loaders and adapters features are:
- automatic (but fully customizable) data binding
- automatic and absolutely transparent cache
- fully asynchronous 
- forced timeouts 
- both [RxJava](https://github.com/ReactiveX/RxJava/tree/1.x) and [RxJava 2](https://github.com/ReactiveX/RxJava) support
- both [Retrofit](http://square.github.io/retrofit/1.x/retrofit/) and [Retrofit 2](http://square.github.io/retrofit/2.x/retrofit/) support
- swipe-to-refresh
- device orientation changing support
- fully customizable progress GUI

In addition, there are the location features which includes:
- both new ([FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)-based)
and old ([GoogleApiClient](https://developers.google.com/android/reference/com/google/android/gms/common/api/GoogleApiClient)-based)
Google Location API support (please visit [Android Developers Blog](https://android-developers.googleblog.com/2017/06/reduce-friction-with-new-location-apis.html) for more info)
- completely auto (but fully customizable via callbacks) permission handling
- tons of boilerplate code are not needed anymore - just annotate your Activity
- Rx support (both [RxJava](https://github.com/ReactiveX/RxJava/tree/1.x) and [RxJava 2](https://github.com/ReactiveX/RxJava))

For more information please refer to the [detailed feature list](https://github.com/akhasoft/Yakhont/wiki).

All kinds of Activities and Fragments (Applications too) are supported: it's not necessary to derive 
them from any predefined ones (with one exception - you will need it for lifecycle debug).

The Yakhont AAR is about 400 KB (except the _full_ version, which is about 620 KB).

Yakhont supports Android 4.0 (API level 14) and above.

**Note:** for lower API levels support (9 and above) please use Yakhont
[v0.9.19](https://github.com/akhasoft/Yakhont/releases/tag/v0.9.19); but the Google Location API
requires API level 14 in any case (please visit
[Android Developers Blog](https://android-developers.googleblog.com/2016/11/google-play-services-and-firebase-for-android-will-support-api-level-14-at-minimum.html)
for more information).

## Feature List

The detailed feature list is available [here](https://github.com/akhasoft/Yakhont/wiki).

## Demo and Releases

Releases are available [here](https://github.com/akhasoft/Yakhont/releases),
demo applications can be downloaded from the
[latest release](https://github.com/akhasoft/Yakhont/releases/latest) (
[demo](https://github.com/akhasoft/Yakhont/releases/download/v0.9.19/yakhont-demo.apk) and
[simplified demo](https://github.com/akhasoft/Yakhont/releases/download/v0.9.19/yakhont-demo-simple.apk)
).

## Versions

- _core_: works with native Android Fragments
- _support_: works with support Fragments (android.support.v4.app.Fragment etc.)
- _full_: core + support + debug classes for most of Activities and Fragments

## Usage

Add the following to your build.gradle (you can use **build.gradle** files from [demo](yakhont-demo/build.gradle)
and [simplified demo](yakhont-demo-simple/build.gradle) as working examples).

1. Update the **buildscript** block:

```groovy
buildscript {
    repositories {
        // your code here, usually just 'jcenter()'
    }
    dependencies {
        classpath 'com.github.akhasoft:yakhont-weaver:0.9.19'
    }
}
```

**Note:** the Yakhont components (com.github.akhasoft:yakhont...) are available from both
[jcenter](http://jcenter.bintray.com/com/github/akhasoft/) and
[mavenCentral](https://oss.sonatype.org/content/repositories/releases/com/github/akhasoft/).

2. Update the **android** block:

```groovy
android {
    // your code here
    
    // fixed DuplicateFileException
    packagingOptions {
        exclude 'META-INF/rxjava.properties'
    }
}
```

3. Update the **dependencies** block:

```groovy
dependencies {
    implementation    'com.github.akhasoft:yakhont:0.9.19'
    // or
    // implementation 'com.github.akhasoft:yakhont-full:0.9.19'
    // or
    // implementation 'com.github.akhasoft:yakhont-support:0.9.19'
}
```

And if you're about to customize Yakhont using build-in [Dagger 2](http://google.github.io/dagger/),
something like following lines are required:

```groovy
dependencies {
    implementation      'com.google.dagger:dagger:2.x'
    annotationProcessor 'com.google.dagger:dagger-compiler:2.x'
}
```

4. The code which runs Yakhont Weaver:

```groovy
// use default config (or specify something like "new String[] {projectDir.absolutePath + '/weaver.config'}")
String[] weaverConfigFiles = null

String pkg = android.defaultConfig.applicationId
boolean weaverDebug = false, weaverAddConfig = true

android.applicationVariants.all { variant ->
    AbstractCompile javaCompile = variant.javaCompiler
    javaCompile.doLast {
        new akha.yakhont.weaver.Weaver().run(variant.buildType.name == 'debug', weaverDebug, pkg,
            javaCompile.destinationDir.toString(), javaCompile.classpath.asPath,
            android.bootClasspath.join(File.pathSeparator), weaverConfigFiles, weaverAddConfig)
    }
}
```

Here the Yakhont Weaver manipulates the Java bytecode just compiled, which makes possible
to alternate classes implementation (e.g. add / modify callbacks in Activities and Fragments)
without changing their source code.

**Note:** the Google "Jack and Jill" technology is not supporting bytecode manipulation.
By the way, Jack is no longer supported too.

5. Finally, don't forget to add to your _AndroidManifest.xml_ something like code snippet below:

```
<application ...>

    ...
    
    <provider
        android:authorities="your_package_name.provider"
        android:name="akha.yakhont.BaseCacheProvider"
        android:enabled="true"
        android:exported="false" />
        
</application>        
```

**Note:** "your_package_name" is a placeholder, you should provide real name, e.g. "com.yourpackage". 

## Weaver: usage and configuration

The Yakhont Weaver is a small but powerful utility which manipulates the compiled Java bytecode
(e.g. in Yakhont demo applications it customizes "Activity.onStart()" and other callbacks).

By default the Yakhont Weaver uses configuration from it's JAR, but you can provide your own
configuration file(s) as a parameter (see above). The "weaverAddConfig = true" means adding your
configuration (if not null) to the default one; "weaverAddConfig = false" forces the Yakhont Weaver
to replace default configuration with yours (even if null).

The Yakhont Weaver is a standalone utility which means it can be used in any application even without 
Yakhont library - just specify "weaverAddConfig = false" and provide your own configuration file.

Please refer to [weaver.config](yakhont/weaver.config) for more details.

## Proguard

ProGuard directives are included in the Yakhont libraries. The Android Plugin for Gradle
automatically appends these directives to your ProGuard configuration.

Anyway, it's strongly advised to keep your BuildConfig, model and Retrofit API
as follows (it's just an example from the Yakhont Demo, 
so please update it according to your application's packages names): 

```
-keep class akha.yakhont.demo.BuildConfig { *; }
-keep class akha.yakhont.demo.model.** { *; }
-keep interface akha.yakhont.demo.retrofit.** { *; }
```

Also, please update the **buildTypes** block with ProGuard directives for 3rd-party libraries
(you can find them [here](proguard/libs)):

```groovy
buildTypes {
    release {
        minifyEnabled true

        proguardFile 'proguard-google-play-services.pro'
        proguardFile 'proguard-gson.pro'
        proguardFile 'proguard-rx-java.pro'
        proguardFile 'proguard-square-okhttp.pro'
        proguardFile 'proguard-square-okhttp3.pro'
        proguardFile 'proguard-square-okio.pro'
        proguardFile 'proguard-square-picasso.pro'
        proguardFile 'proguard-square-retrofit.pro'
        proguardFile 'proguard-square-retrofit2.pro'
        proguardFile 'proguard-support-v7-appcompat.pro'

        // your code here
    }
}
```

## Build

To check out and build the Yakhont source, issue the following commands:

```
$ git clone https://github.com/akhasoft/Yakhont.git
$ cd Yakhont
$ ./gradlew --configure-on-demand yakhont-weaver:clean yakhont-weaver:build
$ ./gradlew --configure-on-demand yakhont:clean yakhont:build
$ ./gradlew --configure-on-demand yakhont-demo:clean yakhont-demo:build
$ ./gradlew --configure-on-demand yakhont-demo-simple:clean yakhont-demo-simple:build
```

**Note:** you may need to update your Android SDK before building.

To avoid some lint issues (in Android Studio, when running Analyze -> Inspect Code):

- add **yakhont.link,yakhont.see** to File -> Settings -> Editor -> Inspections -> Java -> Javadoc -> Declaration has Javadoc problems -> Additional Javadoc Tags
- add [yakhont.dic](yakhont.dic) to File -> Settings -> Editor -> Spelling -> Dictionaries

## Communication

- [GitHub Issues](https://github.com/akhasoft/Yakhont/issues)
- e-mail: [akha.yakhont@gmail.com](mailto:akha.yakhont@gmail.com)

## Information and Documentation

- [Wiki](https://github.com/akhasoft/Yakhont/wiki)
- [Javadoc - core](https://akhasoft.github.io/yakhont/library/core/)
- [Javadoc - full](https://akhasoft.github.io/yakhont/library/full/)
- [Javadoc - support](https://akhasoft.github.io/yakhont/library/support/)
- [Javadoc - weaver](https://akhasoft.github.io/yakhont/weaver/)

## Known Issues

If during project building you got something like Exception below,
kill the Gradle daemon (or just restart your PC).

```
Execution failed for task ':app:transformClassesWithWeaverTransformForDebug'.
> com.yourproject.YourActivity class is frozen
```

## Bugs and Feedback

For bugs, questions and discussions please use the
[Github Issues](https://github.com/akhasoft/Yakhont/issues).

## License

    Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
