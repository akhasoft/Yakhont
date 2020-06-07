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
echo Yakhont Weaver script for patching jars, usage: weave.bat "_cp" _mn, where
echo   _cp - Java class path (should include Yakhont Weaver and Javassist),
echo   _mn - Android project module name to weave (skip for the default one).
echo.
echo Should be called from the root project directory.
echo.

if "%2"=="" (
  set yakhont_weaved_module=app
) else (
  set yakhont_weaved_module=%2
)

rem Create the jar's temp class map
java -cp %1 akha.yakhont.weaver.Weaver 0 %yakhont_weaved_module%
call gradlew --configure-on-demand %yakhont_weaved_module%:clean                               | find /v "> Task :"
java -cp %1 akha.yakhont.weaver.Weaver 1 %yakhont_weaved_module%

rem Handle configs and patch jars
call gradlew --configure-on-demand %yakhont_weaved_module%:build                               | find /v "> Task :"
java -cp %1 akha.yakhont.weaver.Weaver 2

rem Build application and restore jars
call gradlew --configure-on-demand %yakhont_weaved_module%:clean %yakhont_weaved_module%:build | find /v "> Task :"
java -cp %1 akha.yakhont.weaver.Weaver 3
