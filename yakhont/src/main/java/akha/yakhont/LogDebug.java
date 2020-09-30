/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont;

import akha.yakhont.CoreLogger.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the annotation for logging arguments and return value of the annotated method
 * (by default for debug builds only - defined in the 'weaver.config').
 *
 * <br>The idea of {@code LogDebug} came from the Jake Wharton's
 * {@link <a href="https://github.com/JakeWharton/hugo">Hugo</a>} project.
 *
 * <br>{@code LogDebug} works via the Yakhont Weaver, the code to execute defined in the 'weaver.config'
 * (and you can redefine it whatever way you want - 'cause it's just a string which Yakhont Weaver
 * compiles just before weaving).
 *
 * <br>Note: for compilation Yakhont Weaver uses the
 * {@link <a href="http://jboss-javassist.github.io/javassist/">Javassist</a>} library.
 *
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface LogDebug {
    /** The logging level. */
    Level value() default Level.ERROR;  // should be consistent with CoreLogger.getLogDebugLevel()
}
