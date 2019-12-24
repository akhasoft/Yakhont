<!-- Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov

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
        
# Yakhont: all-in-one high-level Android library for data loading, code weaving, location and more

Yes, all in one (well, almost all :-). Data loading (including paging), Rx, progress GUI, cache, data binding,
pull-to-refresh, configuration changing (e.g. portrait / landscape) support - in just one line of code:

```
Retrofit2Loader.start("https://...", YourRetrofit.class, YourRetrofit::method, BR.yourDataBindingID, savedInstanceState);
```

How does it work? Easy. In the code above the Internet address and Retrofit class are provided -
it's enough to create Retrofit client, so Yakhont does it. And runs 'method' (provided too) 
with starting loading progress GUI (fully customizable).

By the way, Yakhont creates the adapter and connects it to your RecyclerView (just scans layout of current
Activity to find it - but of course you can provide the ID; for pull-to-refresh it's exactly the same).

Data received? Yakhont puts it in adapter (and yes, makes data binding - based on 'yourDataBindingID').
No data 'cause of error or timeout? No problem - Yakhont takes it from cache.

Well, cache. For every 'method' the new cache table is automatically created on-the-fly
and filled with data after every successful network request.
Data structure changed? New columns will be automatically added to the table (on-the-fly, too).

And Rx. Just provide
[onNext​](https://www.reactive-streams.org/reactive-streams-1.0.2-javadoc/org/reactivestreams/Subscriber.html#onNext-T-) and
[onError​](https://www.reactive-streams.org/reactive-streams-1.0.2-javadoc/org/reactivestreams/Subscriber.html#onError-java.lang.Throwable-)
callbacks - for Yakhont it's enough. Please refer to
[demo paging](yakhont-demo-simple/src/main/java/akha/yakhont/demosimple/MainActivity.java),
[demo kotlin](yakhont-demo-simple-kotlin/src/main/java/akha/yakhont/demosimplekotlin/MainActivity.kt) and
[demo Room kotlin](yakhont-demo-room-kotlin/src/main/java/akha/yakhont/demoroomkotlin/MainActivity.kt)
for working examples.

What else? Dynamic permissions? Requested and handled fully automatically,
all you need is just to provide permission-specific on-granted-callbacks (please refer to
[dynamic permissions handling](https://akhasoft.github.io/yakhont/library/core/akha/yakhont/CorePermissions.html)).

And all that jazz works from services, too (please refer to
[demo service](yakhont-demo-service/src/main/java/akha/yakhont/demosimple/MainService.java) for more info).

Endless adapter (paging). Configuration changes (e.g. portrait / landscape) surviving.
Transparent gestures recognition / handling. For logging - video / audio recording possibility. And more. 

In sum, as I told - you can make the whole data loading in just one line of code (plus your callbacks - if any).

## Table of Contents

- [Tell Me More](https://github.com/akhasoft/Yakhont#more-info)
- [Feature List](https://github.com/akhasoft/Yakhont#feature-list)
- [Demos and Releases](https://github.com/akhasoft/Yakhont#demo-and-releases)
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

<a href="#more-info" name="more-info"></a>
Some more info about Yakhont. 

If you want to use your own cache (say, Room), adapter or whatever - yes, you can (please refer to 
[demo room kotlin](yakhont-demo-room-kotlin/src/main/java/akha/yakhont/demoroomkotlin)
for the working example).

In addition, there are powerful utility classes which includes:
- completely transparent (but fully customizable) 
[dynamic permissions handling](https://akhasoft.github.io/yakhont/library/core/akha/yakhont/CorePermissions.html)
- extended [logger](https://akhasoft.github.io/yakhont/library/core/akha/yakhont/CoreLogger.html): 
sending logcat error reports (with screenshots and DB copies) on just shaking device (or making Z-gesture), 
stack traces, byte[] logging and more.
- video / audio recording (also via shaking device or making Z-gesture - for more info please refer to the logger mentioned above).
- transparent gestures recognition / handling (i.e. can be used, say, with 
[RecyclerView](https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.html) - also, implemented in logger). 
- more about gestures: you can record and load your own ones (and provide your own handlers for them).
- [reflection](https://akhasoft.github.io/yakhont/library/core/akha/yakhont/CoreReflection.html)
with extended collections support, methods comparing and handling (say, find list of overridden methods), etc.  

And weaving. Imagine, your want to add in all your Activities / Fragments something like this: 

```
public class YourActivity extends Activity {
    @Override
    public void onResume() {
        super.onResume();
        YourClass.someMethod();
    }
}
```

Just put such call in weaver.config - and Yakhont Weaver will add it to all classes you need
(via changing already compiled code with [Javassist](https://www.javassist.org/)). Both Java and Kotlin supported.
And last but not least - Yakhont Weaver supports any applications (i.e. you can use it without Yakhont library).

All-in-one magic mentioned above uses this trick extensively (please refer to
[weaver.config](yakhont/weaver.config) for more details).

**Well, now let's try to talk more officially.**

Yakhont is an Android high-level library offering developer-defined callbacks,
loader wrappers and adapters, fully transparent cache, location-awareness, dynamic permissions handling, 
lifecycle debug classes, advanced reflection, logging and many more helpful developer-oriented features.

There is also the Yakhont Weaver - a small but powerful utility which manipulates the compiled Java (and Kotlin)
bytecode and can be used separately, without the Yakhont library (you will find more info
[below](https://github.com/akhasoft/Yakhont#weaver-usage-and-configuration)).

Now you can load data in a simple but robust way (please refer to
[demo kotlin](yakhont-demo-simple-kotlin/src/main/java/akha/yakhont/demosimplekotlin/MainActivity.kt)
for the working example).

Also, you can take location in a very simple way too (the working example is the same as above):

```
@CallbacksInherited(LocationCallbacks.class)
public class YourActivity extends Activity implements LocationListener {
    @Override
    public void onLocationChanged(Location location, Date date) {
        // your code here
    }
}
```

Yakhont extends the [Application.ActivityLifecycleCallbacks](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html)
approach to support your own callbacks creation, that allows to customize handling of
almost every lifecycle state of your Activities and Fragments - and even without changing
source code (can be useful for libraries developers).

The loader wrappers and adapters are abstracting you away from things like 
loading management, caching, progress dialogs, errors handling and low-level threading;
and don't miss the 'pull-to-refresh' and [RxJava](https://github.com/ReactiveX/RxJava) support.

In short, the data loading features are:
- automatic (but fully customizable) data binding
- automatic and fully transparent cache
- forced timeouts 
- [RxJava](https://github.com/ReactiveX/RxJava) support
- [Retrofit](https://square.github.io/retrofit/2.x/retrofit/) support
- pull-to-refresh
- configuration changes (e.g. portrait / landscape) surviving support
- progress GUI with loading cancellation possibility (yes, fully customizable too)
- endless adapter support (please refer to
[paging demo](yakhont-demo-simple/src/main/java/akha/yakhont/demosimple/MainActivity.java)
for the working example)

In addition, as already told above, there are powerful utility features
(reflection, logging, dynamic permissions handling and more).

For more information please refer to the [detailed feature list](https://github.com/akhasoft/Yakhont/wiki).

All kinds of Activities and Fragments are supported: it's not necessary to derive 
them from some predefined ones (with one exception - you will need it for lifecycle debug).

The Yakhont AAR is about 539 KB (except the _full_ version, which is about 637 KB).

Yakhont supports Android 4.0 (API level 14) and above ('cause support library requires API level 14 as a minimum).

**Note:** for lower API levels support (9 and above) please use Yakhont
[v0.9.19](https://github.com/akhasoft/Yakhont/releases/tag/v0.9.19); but the Google Location API
requires API level 14 in any case (please refer to
[Android Developers Blog](https://android-developers.googleblog.com/2016/11/google-play-services-and-firebase-for-android-will-support-api-level-14-at-minimum.html)
for more information).

## Feature List

The detailed feature list is available [here](https://github.com/akhasoft/Yakhont/wiki).

## Demos and Releases

Releases are available [here](https://github.com/akhasoft/Yakhont/releases),
demo applications can be downloaded from the
[latest release](https://github.com/akhasoft/Yakhont/releases/latest).

1. [demo simple kotlin](https://github.com/akhasoft/Yakhont/releases/download/v/1.2.01/yakhont-demo.apk):
The simplest demo, just loads and displays some list (with "pull-to refresh" support).
2. [demo room kotlin](https://github.com/akhasoft/Yakhont/releases/download/v/1.2.01/yakhont-demo.apk):
The [Google room library](https://developer.android.com/topic/libraries/architecture/room) 
usage demo (+ custom adapter demo).
3. [demo paging](https://github.com/akhasoft/Yakhont/releases/download/v/1.2.01/yakhont-demo-simple.apk):
The endless adapter demo (based on [Google paging library](https://developer.android.com/topic/libraries/architecture/paging)).
4. [demo service](https://github.com/akhasoft/Yakhont/releases/download/v/1.2.01/yakhont-demo.apk):
The simple demo which loads some data - but from [service](https://developer.android.com/reference/android/app/Service.html).
5. [main demo](https://github.com/akhasoft/Yakhont/releases/download/v/1.2.01/yakhont-demo.apk):
The most complex demo with tons of customization.

## Versions

- _core_: main functionality
- _full_: core + Rx1 support + debug classes for Activities and Fragments

## Usage

Add the following to your build.gradle (you can use **build.gradle** files from [demo](yakhont-demo/build.gradle)
and [simplified demo](yakhont-demo-simple-kotlin/build.gradle) as working examples).

1. Update the **buildscript** block (the Yakhont components are available from 
both [jcenter](https://jcenter.bintray.com/com/github/akhasoft/)
and [mavenCentral](https://oss.sonatype.org/content/repositories/releases/com/github/akhasoft/)):

```groovy
buildscript {
    repositories {
        // your code here, e.g. 'jcenter()'
    }
    dependencies {
        classpath 'com.github.akhasoft:yakhont-weaver:1.2.01'
    }
}
```

2. Update the **android** block:

```groovy
android {
    // your code here
    
    packagingOptions {
        exclude 'META-INF/rxjava.properties'
    }
}
```

3. Update the **dependencies** block:

```groovy
dependencies {
    implementation    'com.github.akhasoft:yakhont:1.2.01'
//  or
//  implementation    'com.github.akhasoft:yakhont-full:1.2.01'

//  and if you're going to customize Yakhont using build-in Dagger 2:
    implementation      'com.google.dagger:dagger:2.22.1'           // (or higher)
    annotationProcessor 'com.google.dagger:dagger-compiler:2.22.1'  // (or higher)
//  for Kotlin: please replace 'annotationProcessor' with 'kapt' and add
//  kapt                'com.android.databinding:compiler:3.1.4'    // (or higher)
}
```

4. The code which runs Yakhont Weaver:

    4.1. For Java:
```groovy
// use default config (or provide something like "projectDir.absolutePath + '/yourWeaver.config'")
// or: String[] weaverConfigFiles = new String[] {projectDir.absolutePath + '/yourWeaver.config' /*, ...*/ }
String weaverConfigFiles = null

String pkg = android.defaultConfig.applicationId
boolean weaverDebug = false, weaverAddConfig = true

android.applicationVariants.all { variant ->
    JavaCompile javaCompile = variant.javaCompileProvider.get()
    javaCompile.doLast {
        new akha.yakhont.weaver.Weaver().run(variant.buildType.name == 'debug', weaverDebug, pkg,
            javaCompile.destinationDir.toString(), 
            javaCompile.classpath.asPath, android.bootClasspath.join(File.pathSeparator),
            weaverAddConfig, weaverConfigFiles)
    }
}
```
      4.2. For Kotlin (and Java - optionally):
```groovy
// use default config (or provide something like "projectDir.absolutePath + '/yourWeaver.config'")
// or: String[] weaverConfigFiles = new String[] {projectDir.absolutePath + '/yourWeaver.config' /*, ...*/ }
String weaverConfigFiles = null

String pkg = android.defaultConfig.applicationId, kotlinDir = '/tmp/kotlin-classes/'
boolean weaverDebug = false, weaverAddConfig = true

android.applicationVariants.all { variant ->
    JavaCompile javaCompile  = variant.javaCompileProvider.get()
    javaCompile.doLast {
        String kotlinBase    = buildDir.toString()  + kotlinDir.replace('/', File.separator)
        String kotlinClasses = kotlinBase + 'debug' + File.pathSeparator + kotlinBase + 'release'
        
        new akha.yakhont.weaver.Weaver().run(variant.buildType.name == 'debug', weaverDebug, pkg,
            javaCompile.destinationDir.toString() + File.pathSeparator + kotlinClasses,
            javaCompile.classpath.asPath, android.bootClasspath.join(File.pathSeparator),
            weaverAddConfig, weaverConfigFiles)
    }
}
```

    Here the Yakhont Weaver manipulates the Java (and Kotlin) bytecode just compiled, which makes possible
    to alternate classes implementation (e.g. add / modify callbacks in Activities and Fragments)
    without changing their source code.

5. Finally, please add to your _AndroidManifest.xml_ something like code snippet below
(skip it only if you're not going to use build-in Yakhont cache):

```
<application ...>

    // your code here
    
    <provider
        android:authorities="<your_package_name>.provider"
        android:name="akha.yakhont.BaseCacheProvider"
        android:enabled="true"
        android:exported="false" />
        
</application>        
```

## Weaver: usage and configuration

The Yakhont Weaver is a small but powerful utility which manipulates the compiled Java (and Kotlin) bytecode
(e.g. in Yakhont demo applications it customizes "Activity.onCreate()" and other callbacks).

By default, the Yakhont Weaver uses configuration from it's JAR, but you can provide your own
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
        proguardFile 'proguard-support-design.pro'
        proguardFile 'proguard-gson.pro'
        proguardFile 'proguard-rx-java.pro'
        proguardFile 'proguard-square-okhttp.pro'
        proguardFile 'proguard-square-okhttp3.pro'
        proguardFile 'proguard-square-okio.pro'
        proguardFile 'proguard-square-picasso.pro'
        proguardFile 'proguard-square-retrofit.pro'
        proguardFile 'proguard-square-retrofit2.pro'
        proguardFile 'proguard-support-v7-appcompat.pro'

        // and (optionally)
        proguardFile 'proguard-project.pro'

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
$ ./gradlew --configure-on-demand yakhont:clean        yakhont:build
```
To build Yakhont demo applications, additionally execute the following commands:
```
$ ./gradlew --configure-on-demand yakhont-demo:clean               yakhont-demo:build
$ ./gradlew --configure-on-demand yakhont-demo-simple:clean        yakhont-demo-simple:build
$ ./gradlew --configure-on-demand yakhont-demo-service:clean       yakhont-demo-service:build
$ ./gradlew --configure-on-demand yakhont-demo-simple-kotlin:clean yakhont-demo-simple-kotlin:build
$ ./gradlew --configure-on-demand yakhont-demo-room-kotlin:clean   yakhont-demo-room-kotlin:build
```

**Note:** you may need to update your Android SDK before building.

To avoid some lint issues (in Android Studio, when running Analyze -> Inspect Code)
add [yakhont.dic](yakhont.dic) to File -> Settings -> Editor -> Spelling.

## Communication

- [GitHub Issues](https://github.com/akhasoft/Yakhont/issues)
- e-mail: [akha.yakhont@gmail.com](mailto:akha.yakhont@gmail.com)

## Information and Documentation

- [Wiki](https://github.com/akhasoft/Yakhont/wiki)
- [Javadoc - core](https://akhasoft.github.io/yakhont/1.2.01/library/core/)
- [Javadoc - full](https://akhasoft.github.io/yakhont/1.2.01/library/full/)
- [Javadoc - weaver](https://akhasoft.github.io/yakhont/1.2.01/weaver/)

## Known Issues

If during project building you got something like Exception below,
kill the Gradle daemon (or just restart your PC).

```
Execution failed for task ':app:transformClassesWithWeaverTransformForDebug'.
> com.yourpackage.YourActivity class is frozen
```

## Bugs and Feedback

For bugs, questions and discussions please use the
[Github Issues](https://github.com/akhasoft/Yakhont/issues).

## License

    Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
