/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.callback.annotation;

import akha.yakhont.callback.BaseCallbacks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an annotation for determining what {@link BaseCallbacks} should NOT be applied to the annotated element;
 * <br>automatically inherited.
 *
 * @see BaseCallbacks
 *
 * @author akha
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StopCallbacksInherited {
    /** The list of types derived from {@link BaseCallbacks}. */
    Class<? extends BaseCallbacks>[] value() default {};

    /** The additional info (if any) to pass to the annotated element. */
    String[] parameters() default {};
    /** The additional info (if any) to pass to the annotated element. */
    int   [] properties() default {};
}
