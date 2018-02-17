/*
 * Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.weaver;

import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.NotFoundException;

import org.apache.commons.io.FileUtils;

/**
 * The <code>WeaverTransform</code> class is just the wrapper for the {@link Weaver} one,
 * allowing to use Google's <code>Transform API</code> (deprecated).
 *
 * @see Weaver
 *
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
@Deprecated
public class WeaverTransform extends Transform {

    private final        String                         mApplicationId, mBootClassPath;
    private final        String[]                       mConfigFiles;
    private final        boolean                        mDebug, mAddConfig;

    /**
     * Initialises a newly created {@code WeaverTransform} object.
     *
     * @param debug
     *        The flag which switches ON / OFF printing debug messages to the console
     *
     * @param applicationId
     *        The Android Application ID (normally {@code android.defaultConfig.applicationId} from application's {@code build.gradle})
     *
     * @param bootClassPath
     *        The {@code android.bootClasspath} (actually {@code android.jar}'s location)
     *
     * @param configFiles
     *        The names (and locations) of configuration files (if any), or null (means default one)
     *
     * @param addConfig
     *        Indicates whether the configuration file provided should be added to the default one
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public WeaverTransform(boolean debug, String applicationId, String bootClassPath,
                           String[] configFiles, boolean addConfig) {
        mDebug              = debug;
        mApplicationId      = applicationId;
        mBootClassPath      = bootClassPath;
        mConfigFiles        = configFiles;
        mAddConfig          = addConfig;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public String getName() {
        //noinspection deprecation
        return WeaverTransform.class.getSimpleName();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Set<ContentType> getInputTypes() {
        return Collections.unmodifiableSet(new HashSet<ContentType>(EnumSet.of(DefaultContentType.CLASSES)));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Set<Scope> getScopes() {
        return Collections.unmodifiableSet(EnumSet.of(Scope.PROJECT));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Set<Scope> getReferencedScopes() {
        return Collections.unmodifiableSet(EnumSet.of(Scope.PROJECT_LOCAL_DEPS, Scope.EXTERNAL_LIBRARIES,
                Scope.SUB_PROJECTS, Scope.SUB_PROJECTS_LOCAL_DEPS));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean isIncremental() {
        return false;
    }

    private void add(StringBuilder classPath, QualifiedContent content) throws IOException {
        classPath.append(classPath.length() == 0 ? "": File.pathSeparator).append(content.getFile().getCanonicalPath());
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs,
                          TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        StringBuilder classPath = new StringBuilder();
        for (TransformInput input: referencedInputs) {
            for (DirectoryInput dirInput: input.getDirectoryInputs())
                add(classPath, dirInput);
            for (JarInput jarInput: input.getJarInputs())
                add(classPath, jarInput);
        }

        for (TransformInput input: inputs) {
            for (DirectoryInput dirInput: input.getDirectoryInputs()) {
                File dest = outputProvider.getContentLocation(
                        dirInput.getName(), dirInput.getContentTypes(), dirInput.getScopes(), Format.DIRECTORY);

                FileUtils.copyDirectory(dirInput.getFile(), dest);

                try {
                    new Weaver().run(mDebug, mApplicationId, dest.getCanonicalPath(), classPath.toString(),
                            mBootClassPath, mConfigFiles, mAddConfig);
                }
                catch (NotFoundException | CannotCompileException e) {
                    throw new TransformException(e);
                }
            }
        }
    }
}
