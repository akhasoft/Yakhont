@echo off

rem ****
rem Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem      http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.
rem ****

echo.
echo The Yakhont Weaver patching JARs / AARs script reference implementation.
echo.
echo Usage: weave.bat "_cp" [_mn] [_arg], where
echo   _cp  - Java class path (should include Yakhont Weaver and Javassist JARs),
echo   _mn  - Android project module name to weave (default one is "app"),
echo   _arg - user-defined info to use in build.gradle while weaving JARs / AARs,
echo          can be accessed via project.property("yakhont_weave_arg").
echo.
echo Should be called from the root project directory.
echo.

if "%2"=="" (
  set yakhont_to_weave=app
) else (
  set yakhont_to_weave=%2
)

if "%3"=="" (
  set yakhont_arg=
) else (
  set yakhont_arg=-Pyakhont_weave_arg=%3
)

rem set Yakhont Weaver tmp dir (by default it's a current one)
rem set YAKHONT_WEAVER_TMP_DIR=...

rem init weaving script
java -cp %1 akha.yakhont.weaver.Weaver init

rem make class map and build temp application (just to compile your code)
call gradlew --configure-on-demand %yakhont_arg% %yakhont_to_weave%:clean %yakhont_to_weave%:build | find /v "> Task :"

rem weave JARs / AARs (with your application's code just compiled); 'true' enables debug output to console
java -cp %1 akha.yakhont.weaver.Weaver weave true

rem build final application (with already weaved JARs / AARs)
call gradlew --configure-on-demand               %yakhont_to_weave%:clean %yakhont_to_weave%:build | find /v "> Task :"

rem restore weaved JARs / AARs
java -cp %1 akha.yakhont.weaver.Weaver restore

rem cleanup
java -cp %1 akha.yakhont.weaver.Weaver cleanup

rem set YAKHONT_WEAVER_TMP_DIR=
