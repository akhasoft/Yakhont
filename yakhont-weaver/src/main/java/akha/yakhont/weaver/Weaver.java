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

package akha.yakhont.weaver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Loader;
import javassist.NotFoundException;

//todo change dev to master in weaver.config and script links
/**
 * The <code>Weaver</code> utility weaves the given application's compiled classes (both Java and Kotlin),
 * according to configuration file info; both explicit methods weaving and weaving via annotations are supported.
 *
 * <p>Your code to weave is just a string in some (you can use as many as you want)
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weaver.config">config file</a>};
 * it will be compiled by {@link <a href="https://www.javassist.org/">Javassist</a>} during build process.
 *
 * <p>Please refer to {@link <a href="https://github.com/akhasoft/Yakhont/tree/dev#usage">usage</a>}
 * for build.gradle configuration info.
 *
 * <p>By default, the Yakhont Weaver handles classes from the current project. So, 'android.app.Activity.onResume ...'
 * in config means: weave 'onResume' method in all your Activities that extend 'android.app.Activity'.
 *
 * <p>JARs / AARs weaving supported via &lt;lib&gt; prefix - e.g. we can patch 'Retrofit' JAR (please refer to demo in the default
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weaver.config">weaver.config</a>}).
 * <p>To do this, the build process should be run from the command line -
 * and Yakhont Weaver provides reference scripts implementation (the
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weave">weave</a>} and the
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weave.bat">weave.bat</a>}).
 *
 * <p>For Windows it could be something like this:
 * <pre style="background-color: silver; border: thin solid black;">
 *   jar xf "&lt;Yakhont Weaver jar&gt;" weave.bat
 *   weave.bat "&lt;Yakhont Weaver jar&gt;;&lt;Javassist jar&gt;" [module, default is 'app'] [arg]
 *   del weave.bat
 * </pre>
 *
 * <p>For Unix something like following should work:
 * <pre style="background-color: silver; border: thin solid black;">
 *   jar xf &lt;Yakhont Weaver jar&gt; weave
 *   ./weave &lt;Yakhont Weaver jar&gt;:&lt;Javassist jar&gt; [module, default is 'app'] [arg]
 *   rm ./weave
 * </pre>
 *
 * <p>And well, any JARs / AARs can be patched this way - except signed ones ('cause of classes checksums).
 *
 * <p>If some class to weave exists in more than one library - all these libraries will be weaved.
 *
 * <p>It's also possible to add new methods to already existing classes - please refer to the default
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weaver.config">weaver.config</a>} for more info.
 *
 * <p>For classes, methods and packages names wildcards are also supported: '*' (for many symbols), '?' (for one symbol)
 * and '**' ('*' + all following packages - and yes, you can use '**' with packages only).
 *
 * <p>E.g. 'and**.S?rvi*.on*' will be expanded to
 * <ul>
 *   <li>androidx.lifecycle.ServiceLifecycleDispatcher.onServicePreSuperOnCreate</li>
 *   <li>androidx.lifecycle.ServiceLifecycleDispatcher.onServicePreSuperOnDestroy</li>
 *   <li>androidx.lifecycle.ServiceLifecycleDispatcher.onServicePreSuperOnStart</li>
 *   <li>androidx.lifecycle.ServiceLifecycleDispatcher.onServicePreSuperOnBind</li>
 *   <li>android.app.Service.onTaskRemoved</li>
 *   <li>android.app.Service.onUnbind</li>
 *   <li>android.app.Service.onBind</li>
 *   <li>android.app.Service.onConfigurationChanged</li>
 *   <li>android.app.Service.onRebind</li>
 *   <li>android.app.Service.onTrimMemory</li>
 *   <li>android.app.Service.onDestroy</li>
 *   <li>android.app.Service.onCreate</li>
 *   <li>android.app.Service.onStartCommand</li>
 *   <li>android.app.Service.onStart</li>
 *   <li>android.app.Service.onLowMemory</li>
 *   <li>android.content.ServiceConnection.onServiceDisconnected</li>
 *   <li>android.content.ServiceConnection.onBindingDied</li>
 *   <li>android.content.ServiceConnection.onServiceConnected</li>
 *   <li>android.content.ServiceConnection.onNullBinding</li>
 * </ul>
 *
 * <p>Wildcards support implemented via {@link <a href="https://github.com/google/guava">Guava</a>}'s
 * {@link <a href="https://guava.dev/releases/snapshot-jre/api/docs/com/google/common/reflect/ClassPath.html">ClassPath</a>}.
 * It marked as {@link <a href="https://guava.dev/releases/snapshot-jre/api/docs/com/google/common/annotations/Beta.html">Beta</a>},
 * but what does it mean?
 *
 * <p>Quote from Guava documentation for
 * {@link <a href="https://guava.dev/releases/snapshot-jre/api/docs/com/google/common/annotations/Beta.html">Beta</a>}:
 * <br>"Note that the presence of this annotation implies nothing about the quality or
 * performance of the API in question, only the fact that it is not 'API-frozen'.
 * <br>It is generally safe for applications to depend on beta APIs, at the cost of some extra work during upgrades."
 *
 * <p>So, as you can see, in this case "Beta" doesn't mean the bad quality of the code - it just means
 * thе Guava's API is subject to change. No problem: if it will change, I'll just update the Weaver - and that's all.
 *
 * <p>One more note: weaving of the generated classes (libraries like {@link <a href="https://dagger.dev/">Dagger 2</a>},
 * {@link <a href="https://developer.android.com/topic/libraries/data-binding">Data Binding</a>}, etc.)
 * is not supported for the moment.
 *
 * <p>For more info and working examples please refer to the default
 * {@link <a href="https://github.com/akhasoft/Yakhont/blob/dev/yakhont/weaver.config">weaver.config</a>}
 * configuration file (e.g. new methods should be created with the 'before' keyword and without wildcards).
 *
 * <p>And last but not least - the Yakhont Weaver supports any Java / Kotlin applications
 * (i.e. you can use it even without Yakhont library).
 *
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
public class Weaver {

    private static final String      COMMENT                  = "#"            ;
    private static final String      IGNORE_SIGNATURE         = "(<all>)"      ;
    private static final String      HANDLE_LIB               = "<lib>"        ;
    private static final String      ALIAS_METHOD             = "$method"      ;
    private static final String      ALIAS_CLASS              = "$cls"         ;
    private static final String      DEFAULT_CONFIG_FILE      = "weaver.config";

    private static final String      SCRIPT_INIT              = "init"         ;
    private static final String      SCRIPT_LIBS_WEAVE        = "weave"        ;
    private static final String      SCRIPT_LIBS_RESTORE      = "restore"      ;
    private static final String      SCRIPT_CLEANUP           = "cleanup"      ;

    private static final String      ENV_VAR_TMP_DIR          = "YAKHONT_WEAVER_TMP_DIR"      ;

    private static final String      TMP_PREFIX               = "_tmp_yakhont_weaver_"        ;
    private static final String      TMP_BACKUP               = TMP_PREFIX + "backup.txt"     ;
    private static final String      TMP_TO_HANDLE            = TMP_PREFIX + "to_handle.txt"  ;
    private static final String      TMP_CLASS_MAP            = TMP_PREFIX + "class_map.txt"  ;
    private static final String      TMP_CLASSES              = TMP_PREFIX + "classes.txt"    ;
    private static final String      TMP_FLAG_SCRIPT          = TMP_PREFIX + "flag_script.txt";
    private static final String      TMP_FLAG_1ST_PASS        = TMP_PREFIX + "flag_1st.txt"   ;

    private static final String      NM_PREFIX                = "__"                   ;
    private static final String      NM_ARG_PREFIX            = NM_PREFIX + "arg"      ;
    private static final String      NM_RESULT_NAME           = NM_PREFIX + "result"   ;
    private static final String      NM_EXCEPTION_NAME        = NM_PREFIX + "exception";

    private static final String      CONDITION_DEBUG          = "_D";
    private static final String      CONDITION_RELEASE        = "_R";

    private static final String      MSG_TITLE                = "Yakhont Weaver"                   ;
    private static final String      MSG_ERROR                = "  *** " + MSG_TITLE + " error: "  ;
    private static final String      MSG_WARNING              = "  *** " + MSG_TITLE + " warning: ";

    private static final int         ACTION                   = 0;
    private static final int         ACTION_TOKEN             = 1;
    private static final int         ACTION_CODE              = 2;
    private static final int         ACTION_METHOD            = 3;
    private static final int         ACTION_SIZE              = 4;

    private static final String      sNewLine                 = System.getProperty("line.separator");

    private        final Map<String, Map<String,    List<String[]>>>
                                     mMethodsToWeave          = new LinkedHashMap<>();
    private        final Map<String, Map<String,    List<String[]>>>
                                     mLibMethodsToWeave       = new LinkedHashMap<>();
    private        final Map<String, Map<Condition, List<String[]>>>
                                     mAnnotations             = new LinkedHashMap<>();

    private        final Map<String, String>
                                     mBackup                  = new       HashMap<>();
    private        final Map<String, String>
                                     mToHandle                = new       HashMap<>();
    private              Map<String, List<String[]>>
                                     mClassMap                                       ;
    private              List<File>  mTmpJars                                        ;

    @SuppressWarnings("UnstableApiUsage")
    private              ImmutableSet<ClassInfo>
                                     mAllClasses                                     ;
    private              WildCardsHandler
                                     mWildCardsHandler                               ;

    private static final Set<String> sWarnings                = new       HashSet<>();

    private              String      mPackageName  ;
    private              String      mClassPath    ;
    private              String      mBootClassPath;

    private              boolean     mDebug        ;
    private              boolean     mDebugBuild   ;
    private              boolean     mUpdated      ;

    private enum Action {
        INSERT_BEFORE,
        INSERT_AFTER,
        INSERT_AFTER_FINALLY,
        CATCH
    }

    /**
     * For the moment the build types only.
     */
    protected enum Condition {
        /** Build type not defined. */
        NOT_DEFINED,
        /** The debug build. */
        DEBUG,
        /** The release build. */
        RELEASE
    }

    /**
     * Initialises a newly created {@code Weaver} object.
     */
    protected Weaver() {
    }

    private static String getTmpDir() {
        String tmpDir = null;
        try {
            tmpDir = System.getenv(ENV_VAR_TMP_DIR);
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
        if (tmpDir == null) tmpDir = ".";

        return tmpDir.endsWith(File.separator) ? tmpDir: tmpDir + File.separator;
    }

    /**
     * Prints debug message to the console.
     *
     * @param message message to print
     */
    @SuppressWarnings("WeakerAccess")
    protected void log(String message) {
        log(!mDebug, message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static void log(boolean silent, String message) {
        if (!silent) System.out.println(message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static void logError(String message) {
        log(false, sNewLine + MSG_ERROR + message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static void logWarning(String message) {
        if (sWarnings.contains(message)) return;
        sWarnings.add(message);

        log(false, sNewLine + MSG_WARNING + message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static void logForce(String message) {
        logForce(null, message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static void logForce(String prefix, String message) {
        log(false, (prefix == null ? "": prefix) + MSG_TITLE + ": " + message);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue"})
    protected static void log2ndPass(String prefix, String message) {
        if (!checkFlag(getTmpDir() + TMP_FLAG_1ST_PASS))
            log(false, (prefix == null ? "": prefix) + MSG_TITLE + ": " + message);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void main(String[] parameters) throws IOException {
        switch (parameters[0].toLowerCase()) {
            case SCRIPT_INIT:
                initScript();
                break;
            case SCRIPT_LIBS_WEAVE:
                weaveLibs(Boolean.parseBoolean(parameters[1]));
                break;
            case SCRIPT_LIBS_RESTORE:
                restoreLibs();
                break;
            case SCRIPT_CLEANUP:
                cleanup();
                break;
            default:
                logError("unknown argument: " + parameters[0]);
                break;
        }
    }

    private static Map<String, List<String[]>> getClassMap(boolean debug) throws IOException {
        Map<String, List<String[]>> classMap = new HashMap<>();
        Set<String> jetified = new HashSet<>(), api = new HashSet<>();

        for (String line: read(getTmpDir() + TMP_CLASS_MAP)) {
            String[] tmp = parse(line);
            String   cls = tmp[0];
            String   jar = tmp[tmp.length - 1];

            String jarName = getJarName(jar);
            boolean tmpJarFromAar = isTmpJarFromAar(jarName), jetifiedJar = isJetifiedJar(jarName);

            if (tmpJarFromAar   ) reportJarProblem(api     , "JAR from AAR", jar, debug);
            else if (jetifiedJar) reportJarProblem(jetified, "jetified JAR", jar, debug);

            // could be uncommented to increase performance (if class map contains such files);
            //   please refer to makeClassMap() for more info
//          if (tmpJarFromAar || jetifiedJar) continue;

            int    pos = cls.length() - 6;
            String key = cls.substring(0, pos);

            if (!classMap.containsKey(key)) classMap.put(key, new ArrayList<>());

            String[] data = new String[tmp.length];
            data[0] = cls.substring(pos);
            System.arraycopy(tmp, 1, data, 1, tmp.length - 1);

            classMap.get(key).add(data);
        }

        return classMap;
    }

    private static String getJarName(String jar) {
        return jar.substring(jar.lastIndexOf(File.separator) + 1).toLowerCase();
    }

    private static boolean isJetifiedJar(String jarName) {
        return jarName.startsWith("jetified-");
    }

    private static boolean isTmpJarFromAar(String jarName) {
        return jarName.endsWith  ("-api.jar" );
    }

    private static void reportJarProblem(Set<String> set, String info, String jar, boolean debug) {
        if (!debug || set.contains(jar)) return;

        log(false, MSG_TITLE + ": " + info +
                " is not subject to weave ('cause it's a temp file) - '" + jar + "'");
        set.add(jar);
    }

    private static void weaveLibs(boolean debug) throws IOException {
        delete(getTmpDir() + TMP_FLAG_1ST_PASS);

        Map<String, List<String[]>> classMap   = getClassMap(false);
        LibHandler                  libHandler = new LibHandler(debug);

        for (String line: read(getTmpDir() + TMP_TO_HANDLE)) {
            String[]       data = parse(line);
            String          cls = data[0];
            String         file = data[1];
            List<String[]> libs = classMap.get(cls);

            if (libs == null || libs.size() == 0)
                logError("can't find lib for class " + getClassName(cls) + "; make sure you have " +
                        "'afterEvaluate { ... Weaver.makeClassMap(...) }' in your build.gradle");
            else
                for (String[] tmp: libs) {
                    if (tmp.length == 2) {
                        String jarName = getJarName(tmp[1]);

                        if (jarName.equals("android.jar"))
                            logError("'" + cls + ".class' - weaving 'android.jar" +
                                    "' is pointless 'cause it hardcoded in devices");
                        else if (isTmpJarFromAar(jarName) || isJetifiedJar(jarName))
                            logError("'" + cls + ".class' - weaving '" + tmp[1] +
                                    "' is pointless 'cause it's a temp file");

                        libHandler.replace(tmp[1], cls + tmp[0], file);
                    }
                    else
                        try {
                            List<File[]> files = LibHandler.getAarLibAsTmpFiles(new File(tmp[1]),
                                    Arrays.asList(Arrays.copyOfRange(tmp, 1, tmp.length)));
                            libHandler.replace(files.get(files.size() - 1)[0].getAbsolutePath(),
                                    cls + tmp[0], file);

                            for (int i = files.size() - 2; i >= 0; i--) {
                                File[] newFile = files.get(i + 1);
                                libHandler.replace(files.get(i)[0].getAbsolutePath(), tmp[i + 2],
                                        newFile[0].getAbsolutePath());
                                delete(newFile[1]);
                            }
                        }
                        catch (/*IO*/Exception exception) {
                            exception.printStackTrace();
                        }
                    log(!debug, sNewLine);
                }
            delete(data[2]);
        }
    }

    private static String getClassName(String cls) {
        return "'" + cls.replace('/', '.') + "'";
    }

    private static void cleanup() {
        File tmpClasses = new File(getTmpDir() + TMP_CLASSES);
        if (!tmpClasses.exists()) return;

        try {
            for (String line: read(tmpClasses))
                delete(line);
        }
        catch (/*IO*/Exception exception) {
            exception.printStackTrace();
        }
        delete(tmpClasses);
    }

    private static void restoreLibs() {
        String tmpDir = getTmpDir();

        delete(tmpDir + TMP_CLASS_MAP  );
        delete(tmpDir + TMP_TO_HANDLE  );
        delete(tmpDir + TMP_FLAG_SCRIPT);

        File backup = new File(tmpDir + TMP_BACKUP);
        if (!backup.exists()) return;

        try {
            boolean ok = true;
            for (String line: read(backup)) {
                String[] tmp     = parse(line);
                File  [] srcDest = new File[] {new File(tmp[1]), new File(tmp[0])};

                String fromTo = "'" + srcDest[1].getAbsolutePath() + "' from '" +
                        srcDest[0].getAbsolutePath() + "'";
                try {
                    backupAndRestore(srcDest[0], srcDest[1]);
                    logForce(sNewLine, "restored " + fromTo);

                    delete(srcDest[0]);
                }
                catch (IOException exception) {
                    ok = false;

                    logError("failed to restore " + fromTo);
                    exception.printStackTrace();
                }
            }
            if (ok) delete(backup);
        }
        catch (/*IO*/Exception exception) {
            exception.printStackTrace();
        }

        cleanup();

        if (backup.exists())
            logError("failed to restore files, the list of them is in " + backup.getAbsolutePath() +
                    sNewLine + "don't run build process again - try to manually restore files first");
    }

    private static void createFlag(String name) throws IOException {
        File flag = new File(name);
        if (!flag.createNewFile()) logError("can't create '" + flag.getAbsolutePath() + "'");
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean checkFlag(String name) {
        return new File(name).exists();
    }

    private static void initScript() throws IOException {
        String tmpDir = getTmpDir();

        delete(tmpDir + TMP_CLASS_MAP);
        delete(tmpDir + TMP_CLASSES  );
        delete(tmpDir + TMP_TO_HANDLE);
        delete(tmpDir + TMP_BACKUP   );

        createFlag(tmpDir + TMP_FLAG_SCRIPT  );
        createFlag(tmpDir + TMP_FLAG_1ST_PASS);
    }

    /**
     * Makes class map (containing info about JARs / AARs and classes inside them).
     * <br>Used for JARs / AARs weaving only.
     *
     * @param libs
     *        The collection of JARs / AARs
     *
     * @param debug
     *        The flag which switches ON / OFF printing debug messages to the console
     *
     * @param noTmp
     *        The flag which switches ON / OFF using tmp JARs (jetified or from AARs) in class map
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void makeClassMap(Collection<File> libs, boolean debug, boolean noTmp) {
        if (!checkFlag(getTmpDir() + TMP_FLAG_1ST_PASS)) return;

        File classMap = new File(getTmpDir() + TMP_CLASS_MAP);
        if (classMap.exists()) return;

        logForce(sNewLine, "about to create temporarily class map '" +
                classMap.getAbsolutePath() + "'...");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(classMap));
            try {
                for (File lib: libs)
                    LibHandler.classMapHandle(writer, lib, debug, noTmp);
            }
            finally {
                try {
                    writer.close();
                }
                catch (Exception exception) {
                    classMapHandleError(exception);
                }
            }
        }
        catch (Exception exception) {
            classMapHandleError(exception);
        }
    }

    private static void classMapHandleError(Exception exception) {
        logError(exception.toString());
    }

    /**
     * Starts weaving.
     *
     * @param debug
     *        The flag which switches ON / OFF printing debug messages to the console
     *
     * @param packageName
     *        The application's package name
     *
     * @param classesDir
     *        The location of the given application's compiled classes
     *
     * @param classPath
     *        The Java compiler's class path, used to compile the given application
     *
     * @param bootClassPath
     *        The {@code android.bootClasspath} (actually {@code android.jar}'s location)
     *
     * @param addConfig
     *        Indicates whether the configuration file(s) provided should be added to the default one
     *
     * @param configFiles
     *        The names (and locations) of configuration files (if any), or null (means the default one)
     *
     * @throws  NotFoundException
     *          please refer to the exception description
     * @throws  CannotCompileException
     *          please refer to the exception description
     * @throws  IOException
     *          please refer to the exception description
     */
    @Deprecated
    public static void run(boolean debug, String packageName, String classesDir, String classPath,
                           String bootClassPath, boolean addConfig, String... configFiles)
            throws NotFoundException, CannotCompileException, IOException {
        if (classesDir.endsWith("debug"))
            run(true, debug, packageName, classesDir, classPath, bootClassPath,
                    addConfig, configFiles);
        else if (classesDir.endsWith("release"))
            run(false, debug, packageName, classesDir, classPath, bootClassPath,
                    addConfig, configFiles);
        else
            logError("can't detect build type");
    }

    /**
     * Starts weaving.
     *
     * @param debugBuild
     *        The flag which indicates whether the given build is debug one or not
     *
     * @param debug
     *        The flag which switches ON / OFF printing debug messages to the console
     *
     * @param packageName
     *        The application's package name
     *
     * @param classesDirs
     *        The location(s) of the given application's compiled classes
     *
     * @param classPath
     *        The Java compiler's class path, used to compile the given application
     *
     * @param bootClassPath
     *        The {@code android.bootClasspath} (actually {@code android.jar}'s location)
     *
     * @param addConfig
     *        Indicates whether the configuration file(s) provided should be added to the default one
     *
     * @param configFiles
     *        The names (and locations) of configuration files (if any), or null (means the default one)
     *
     * @throws  NotFoundException
     *          please refer to the exception description
     * @throws  CannotCompileException
     *          please refer to the exception description
     * @throws  IOException
     *          please refer to the exception description
     */
    public static void run(boolean debugBuild, boolean debug, String packageName, String classesDirs,
                           String classPath, String bootClassPath, boolean addConfig, String... configFiles)
            throws NotFoundException, CannotCompileException, IOException {
        log2ndPass(sNewLine, "weaving classes in [" + classesDirs + "]...");

        try {
            new Weaver().run(debugBuild, debug, packageName, classesDirs, classPath, bootClassPath,
                    configFiles, addConfig);
        }
        catch (Exception exception) {
            exception.printStackTrace();
            throw exception;
        }
    }

    private void run(boolean debugBuild, boolean debug, String packageName, String classesDirs,
                     String classPath, String bootClassPath, String[] configFiles, boolean addConfig)
            throws NotFoundException, CannotCompileException, IOException {
        mUpdated    = false;

        mDebugBuild = debugBuild;
        mDebug      = debug;
        log(sNewLine + "debug build: " + mDebugBuild + ", debug: " + mDebug);

        mClassPath     = classPath;
        mBootClassPath = bootClassPath;
        logPaths();

        mPackageName   = packageName;
        log(sNewLine + "package name: " + packageName);

        sWarnings         .clear();

        mAnnotations      .clear();
        mMethodsToWeave   .clear();
        mLibMethodsToWeave.clear();

        mAllClasses         = null;
        mWildCardsHandler   = null;

        mTmpJars            = new ArrayList<>();
        try {
            run(classesDirs, configFiles, addConfig);
        }
        finally {
            for (File tmpJar: mTmpJars)
                delete(tmpJar);
        }
    }

    private void run(String classesDirs, String[] configFiles, boolean addConfig)
            throws NotFoundException, CannotCompileException, IOException {

        if (addConfig) parseConfig();
        if (configFiles != null) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < configFiles.length; i++)
                parseConfig(configFiles[i]);
        }
        printConfig(mMethodsToWeave, mLibMethodsToWeave, mAnnotations);

        validateMethods    (mMethodsToWeave   );
        validateMethods    (mLibMethodsToWeave);
        validateAnnotations();

        String tmpDir = getTmpDir();

        mToHandle.clear();
        delete(tmpDir + TMP_TO_HANDLE);

        mBackup.clear();

        File backup = new File(tmpDir + TMP_BACKUP);
        if (backup.exists())
            for (String line: read(backup)) {
                String[] tmp = parse(line);
                mBackup.put(tmp[0], tmp[1]);
            }

        if (mLibMethodsToWeave.size() == 0)
            delete(tmpDir + TMP_CLASS_MAP);
        else if (new File(tmpDir + TMP_CLASS_MAP).exists())
            mClassMap = getClassMap(mDebug);

        // handle project classes
        for (String classesDir: classesDirs.split(File.pathSeparator)) {
            log(sNewLine + sNewLine + "about to weave classes in [" + classesDir + "]" + sNewLine);
            searchClasses(new File(classesDir).listFiles());
        }

        // handle jars
        for (String key: mLibMethodsToWeave.keySet())
            weave(key);
        write(mToHandle, tmpDir + TMP_TO_HANDLE);
        write(mBackup  , tmpDir + TMP_BACKUP   );

        if (mMethodsToWeave.size() + mLibMethodsToWeave.size() + mAnnotations.size() > 0 && !mUpdated)
            logWarning("no classes to weave");
    }

    private static void write(Map<String, String> map, String file) {
        delete(file);
        for (Map.Entry<String, String> entry: map.entrySet())
            write(file, entry.getKey() + File.pathSeparator + entry.getValue());
    }

    private void logPaths() {
        logPaths(     "classpath", mClassPath    );
        logPaths("boot classpath", mBootClassPath);
    }

    private void logPaths(String pathName, String paths) {
        log(sNewLine + pathName + ":");
        for (String path: paths.split(File.pathSeparator))
            log(path);
    }

    private static String[] parse(String line) {
        return line.split(File.pathSeparator);
    }

    private void parseConfig() throws IOException, CannotCompileException, NotFoundException {
        log(sNewLine + sNewLine + "config file: default");

        for (String line: read(new InputStreamReader(Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)))))
            parseConfig(mMethodsToWeave, mLibMethodsToWeave, mAnnotations, line);
    }

    private void parseConfig(String configFile) throws IOException, CannotCompileException, NotFoundException {
        if (configFile == null) return;

        configFile = configFile.replace('\\', File.separatorChar).replace('/', File.separatorChar);
        log(sNewLine + sNewLine + "config file: " + configFile);

        for (String line: read(configFile))
            parseConfig(mMethodsToWeave, mLibMethodsToWeave, mAnnotations, line);
    }

    /**
     * Parses the configuration file.
     *
     * @param mapM
     *        The internal representation of methods configuration info
     *
     * @param mapL
     *        The internal representation of methods configuration info for JARs / AARs
     *
     * @param mapA
     *        The internal representation of annotations configuration info
     *
     * @param line
     *        The line to parse
     *
     * @throws  CannotCompileException
     *          please refer to the exception description
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    protected void parseConfig(Map<String, Map<String   , List<String[]>>> mapM,
                               Map<String, Map<String   , List<String[]>>> mapL,
                               Map<String, Map<Condition, List<String[]>>> mapA,
                               String line) throws CannotCompileException, NotFoundException {
        if (line          == null) return;
        line = line.replace('\t', ' ').trim();
        if (line.length() ==    0) return;

        int idx = line.indexOf(COMMENT);
        if (idx == 0) return;
        if (idx >  0) line = line.substring(0, idx);

        boolean isLib = line.startsWith(HANDLE_LIB);

        if (isLib) line = line.substring(HANDLE_LIB.length()).trim();
        Map<String, Map<String, List<String[]>>> mapMethods = isLib ? mapL: mapM;

        List<String> tokens = new LinkedList<>();
        for (Matcher m = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'").matcher(line.trim()); m.find();)
            tokens.add(m.group().replace("'", ""));

        if (tokens.size() == 0) return;
        if (tokens.size() <  3) throw new CannotCompileException("error - invalid line (should be at least 3 tokens): " + line);

        String pattern = tokens.get(getClassOffset(tokens, line));

        if (!hasWildCards(pattern)) {
            parseConfigHelper(mapMethods, mapA, tokens, line);
            return;
        }
        if (!pattern.contains("$")) {
            handleWildCards(mapMethods, mapA, null, tokens, line);
            return;
        }

        List<String> patterns = WildCardsHandler.split(pattern, "$");

        @SuppressWarnings("UnstableApiUsage")
        List<ClassInfo> topClassesInfo = new ArrayList<>(
                handleWildCards(null, null, patterns.get(0), null, line));
        if (topClassesInfo.size() == 0) return;

        List<String> tmpPatterns = new ArrayList<>(
                patterns.subList(1, patterns.size() - 1));

        String lastPattern = patterns.get(patterns.size() - 1);

        idx = lastPattern.indexOf('.');
        if (idx >= 0)
            tmpPatterns.add(lastPattern.substring(0, idx));
        else {
            logError("invalid pattern: '" + pattern + "'");
            return;
        }
        String methodPattern = lastPattern.substring(idx + 1).trim();

        logAddHeader(pattern);

        boolean[] found = new boolean[1];
        for (Class<?> cls: mWildCardsHandler.getDeclaredClassesAll(tmpPatterns, topClassesInfo))
            addMethod(mapMethods, mapA, methodPattern, tokens, found, cls, cls.getName(), line);

        logAddResults(found);
    }

    private static void logAddHeader(String pattern) {
        if (!checkFlag(getTmpDir() + TMP_FLAG_1ST_PASS)) log(false,
                sNewLine + sNewLine + MSG_TITLE + " added for pattern '" + pattern + "':");
    }

    private static void logAddResults(boolean[] found) {
        if (!found[0] && !checkFlag(getTmpDir() + TMP_FLAG_1ST_PASS))
            log(false, "  nothing");
    }

    private static boolean hasWildCards(String string) {
        return string.contains("*") || string.contains("?");
    }

    private static int getClassOffset(List<String> tokens, String line) throws CannotCompileException {
        for (int i = 0; i < tokens.size(); i++)
            if (tokens.get(i).contains(".")) return i;
        throw new CannotCompileException("error - invalid line: " + line);
    }

    @SuppressWarnings("UnstableApiUsage")
    private List<ClassInfo> handleWildCards(Map<String, Map<String   , List<String[]>>> mapM,
                                            Map<String, Map<Condition, List<String[]>>> mapA,
                                            String classPattern, List<String> tokens, String line)
            throws CannotCompileException, NotFoundException {
        if (mWildCardsHandler == null) {
            mWildCardsHandler = new WildCardsHandler(mDebug);
            mWildCardsHandler.addClassPath(mClassPath);
            mWildCardsHandler.addClassPath(mBootClassPath);
        }
        List<ClassInfo> results = classPattern == null ? Collections.emptyList(): new ArrayList<>();

        if (mAllClasses == null)
            mAllClasses = mWildCardsHandler.getClassPath(getClassPool()).getTopLevelClasses();
        if (mAllClasses == null) {
            logError("can't get classes list for handling wildcards");
            return results;
        }

        if (classPattern != null) {
            for (ClassInfo classInfo: mAllClasses)
                if (WildCardsHandler.matchesClass(classPattern, classInfo.getName())) results.add(classInfo);
            return results;
        }

        String methodPattern;
        String name       = tokens.get(getClassOffset(tokens, line));

        int methodDotPos  = WildCardsHandler.getMethodDotPos(name);
        if (methodDotPos >= 0) {
            classPattern  = name.substring(0, methodDotPos);
            methodPattern = name.substring(methodDotPos + 1).trim();
        }
        else {              // should never happen
            logError("can't get classes list for handling wildcards");
            return results;
        }

        logAddHeader(name);

        boolean[] found = new boolean[1];
        for (ClassInfo classInfo: mAllClasses) {
            String className = classInfo.getName();

            if (WildCardsHandler.matchesClass(classPattern, className))
                addMethod(mapM, mapA, methodPattern, tokens, found,
                        WildCardsHandler.getClass(classInfo), className, line);
        }
        logAddResults(found);

        return results;
    }

    private void addMethod(Map<String, Map<String   , List<String[]>>> mapM,
                           Map<String, Map<Condition, List<String[]>>> mapA,
                           String methodPattern, List<String> tokens, boolean[] found,
                           Class<?> cls, String className, String line) throws CannotCompileException {

        if (isAnnotation(methodPattern))
            addMethod(mapM, mapA, tokens, className, methodPattern, found, line);
        else
            for (String method: mWildCardsHandler.getDeclaredMethods(methodPattern, cls))
                addMethod(mapM, mapA, tokens, className, method, found, line);
    }

    private static void addMethod(Map<String, Map<String   , List<String[]>>> mapM,
                                  Map<String, Map<Condition, List<String[]>>> mapA,
                                  List<String> tokens, String className, String methodName,
                                  boolean[] found, String line) throws CannotCompileException {
        tokens.set(0, className + "." + methodName);
        parseConfigHelper(mapM, mapA, tokens, line);
        found[0] = true;
        if (!checkFlag(getTmpDir() + TMP_FLAG_1ST_PASS))
            log(false, "  " + tokens.get(getClassOffset(tokens, line)));
    }

    private static void parseConfigHelper(Map<String, Map<String   , List<String[]>>> mapM,
                                          Map<String, Map<Condition, List<String[]>>> mapA,
                                          List<String> tokens, String line) throws CannotCompileException {
        Action action;
        String actionToken = tokens.get(tokens.size() - 2);

        switch (actionToken.toLowerCase()) {
            case "before":
                action = Action.INSERT_BEFORE;
                break;
            case "after":
                action = Action.INSERT_AFTER;
                break;
            case "finally":
                action = Action.INSERT_AFTER_FINALLY;
                break;
            default:
                action = Action.CATCH;
                break;
        }

        int classOffset = getClassOffset(tokens, line);
        if (isNewSpecialMethod(tokens)) {
            String name = tokens.get(classOffset);

            int posParenthesis  = name.indexOf('(');
            if (posParenthesis >= 0) {
                tokens.set(classOffset, name.substring(0, posParenthesis));
                tokens.add(classOffset + 1, name.substring(posParenthesis));
            }
        }
        String       name = tokens.get(classOffset);
        String methodName = name.substring(name.lastIndexOf('.') + 1);
        String  className = name.substring(0, name.length() - methodName.length() - 1);

        if (isAnnotation(methodName)) {
            Condition condition;
            switch (methodName.toUpperCase()) {
                case CONDITION_RELEASE:
                    condition = Condition.RELEASE;
                    break;
                case CONDITION_DEBUG:
                    condition = Condition.DEBUG;
                    break;
                default:        // fall through
                    logError("unknown annotation qualifier: " + methodName);
                case "":
                    condition = Condition.NOT_DEFINED;
            }
            put(className,  condition, action, actionToken, tokens, mapA, classOffset);
        }
        else
            put(className, methodName, action, actionToken, tokens, mapM, classOffset);
    }

    private static boolean isAnnotation(String methodName) {
        return methodName.length() == 0             ||
               methodName.equals(CONDITION_RELEASE) || methodName.equals(CONDITION_DEBUG);
    }

    private static <T> void put(String className, T key, Action action, String actionToken,
                                List<String> tokens, Map<String, Map<T, List<String[]>>> map, int classOffset) {
        if (!map.containsKey(className))    //noinspection Convert2Diamond
            map.put(className, new LinkedHashMap<T, List<String[]>>());
        Map<T, List<String[]>> classMap = map.get(className);

        if (!classMap.containsKey(key))     //noinspection Convert2Diamond
            classMap.put(key, new ArrayList<String[]>());

        String[] data = new String[ACTION_SIZE];
        data[ACTION      ] = String.valueOf(action.ordinal());
        data[ACTION_TOKEN] = actionToken;
        data[ACTION_CODE ] = removeExtraSpaces(tokens.get(tokens.size() - 1));

        if (isNewSpecialMethod(tokens)) {
            StringBuilder method = new StringBuilder();
            for (int i = 0; i < tokens.size() - 2; i++) {
                String token = tokens.get(i);
                method.append(i != classOffset ? token:
                        token.substring(token.lastIndexOf('.') + 1)).append(' ');
            }
            data[ACTION_METHOD] = method.toString().trim();
        }

        classMap.get(key).add(data);
    }

    private static boolean isNewSpecialMethod(List<String> tokens) {
        return tokens.size() > 3;
    }

    private <T> void print(Map<String, Map<T, List<String[]>>> map, String title) {
        if (map.isEmpty()) return;
        log(String.format("%s- %s", sNewLine, title));

        for (String key: map.keySet()) {
            log(String.format("%s--- %s", sNewLine, key));

            for (T subKey: map.get(key).keySet()) {
                log(String.format("%s   %s", sNewLine, subKey));

                for (String[] data: map.get(key).get(subKey))
                    log(String.format("    action: %s, code: '%s', method: '%s'",
                            getActionDescription(data), data[ACTION_CODE], data[ACTION_METHOD]));
            }
        }
    }

    private void printConfig(Map<String, Map<String   , List<String[]>>> mapM,
                             Map<String, Map<String   , List<String[]>>> mapL,
                             Map<String, Map<Condition, List<String[]>>> mapA) {
        log(sNewLine + "START OF CONFIG");

        if (mapA.isEmpty())                   log(sNewLine + "No annotations defined");
        if (mapM.isEmpty() && mapL.isEmpty()) log(sNewLine + "No methods defined"    );

        print(mapA, "ANNOTATIONS");
        print(mapM, "METHODS"    );
        print(mapL, "LIB METHODS");

        log(sNewLine + "END OF CONFIG");
    }

    private static String removeExtraSpaces(String str) {
        return str.replaceAll("\\s+", " ");
    }

    private static Action getAction(String[] methodData) {
        return Action.values()[Integer.parseInt(methodData[ACTION])];
    }

    private static String getActionDescription(String[] methodData) {
        Action action = getAction(methodData);
        return (action.equals(Action.CATCH) ? "catch ": "") + methodData[ACTION_TOKEN];
    }

    private void searchClasses(File[] files) throws NotFoundException, CannotCompileException, IOException {
        if (files == null) return;

        for (File file: files)
            if (file.isDirectory())
                searchClasses(file.listFiles());
            else
                weave(file);
    }

    private void weave(File destClassFile) throws NotFoundException, CannotCompileException, IOException {
        String destClassName, path = destClassFile.getCanonicalPath();

        int pos = path.indexOf(mPackageName.replace('.', File.separatorChar));
        if (pos < 0) return;

        destClassName = path.substring(pos).replace(File.separatorChar, '.')
                .substring(0, path.length() - pos - 6);
        String buildDir = path.substring(0, pos);

        ClassPool classPool = getClassPool();
        classPool.insertClassPath(buildDir.endsWith(File.separator) ?
                buildDir.substring(0, buildDir.length() - 1): buildDir);

        CtClass classDest = classPool.get(destClassName);
        if (!classDest.getPackageName().startsWith(mPackageName)) return;

        handleAnnotations(classDest, classPool, true);
        handleMethods    (classDest, classPool, destClassName, mMethodsToWeave);
        handleAnnotations(classDest, classPool, false);

        if (!classDest.isModified()) return;

        log(sNewLine + "about to write class " + classDest.getName() + " to " + buildDir);
        handleClassDest(classDest, buildDir, false);

        mUpdated = true;
    }

    private void weave(String destClassName)
            throws NotFoundException, CannotCompileException, IOException {
        ClassPool classPool = getClassPool();
        CtClass classDest = classPool.get(destClassName);

        handleMethods(classDest, classPool, destClassName, mLibMethodsToWeave);

        if (!classDest.isModified()) return;

        String tmpDir = Files.createTempDirectory(null).toFile().getAbsolutePath();

        log(sNewLine + "about to write class " + classDest.getName() + " to " + tmpDir);
        handleClassDest(classDest, tmpDir, true);

        mUpdated = true;
    }

    private void handleClassDest(CtClass classDest, String newClassDir, boolean isLib)
            throws CannotCompileException, IOException {
        if (!newClassDir.endsWith(File.separator)) newClassDir += File.separator;

        classDest.writeFile(newClassDir);
        if (!isLib) return;

        if (!checkFlag(getTmpDir() + TMP_FLAG_SCRIPT)) {
            delete(newClassDir);
            return;
        }

        write(getTmpDir() + TMP_CLASSES, newClassDir);

        String className = classDest.getName();
        String newClassFileName = newClassDir + className.replace('.', File.separatorChar) + ".class";
        className = className.replace('.', '/');

        List<String[]> data = mClassMap == null ? null: mClassMap.get(className);
        if (data == null) {
            if (mClassMap != null) logError("can't find class " + getClassName(className) + " in the class map");
        }
        else
            for (String[] tmp: data) {
                String lib = tmp[1];

                if (!mBackup.containsKey(lib)) {
                    File   libFile = new File(lib);
                    String libName = libFile.getName();

                    File backup = File.createTempFile(libName.substring(
                            0, libName.lastIndexOf('.') + 1), lib.substring(lib.length() - 4));
                    backupAndRestore(libFile, backup);

                    String backupName = backup.getAbsolutePath();
                    mBackup.put(lib, backupName);

                    logForce(sNewLine, "backed up '" + libFile.getAbsolutePath() + "' to '" +
                            backupName + "'");
                }
            }

        if (mToHandle.containsKey(className))     // should never happen
            logError("class '" + className + "' is already weaved");
        else
            mToHandle.put(className, newClassFileName + File.pathSeparator + newClassDir);
    }

    private static void backupAndRestore(File source, File destination) throws IOException {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private ClassPool getClassPool() throws NotFoundException {
        // ClassPool.getDefault() returns singleton, but we need new instance
        ClassPool classPool = new ClassPool(true);

        LibHandler libHandler = new LibHandler(mDebug);

        appendPaths(classPool, mClassPath    , mTmpJars, libHandler);
        appendPaths(classPool, mBootClassPath, mTmpJars, libHandler);

        return classPool;
    }

    private static void appendPaths(ClassPool classPool, String paths, List<File> tmpJars,
                                    LibHandler libHandler) throws NotFoundException {
        for (String path: paths.split(File.pathSeparator))
            if (getExtension(path).equals(".aar"))
                for (File jar: libHandler.getJars(path, null)) {
                    classPool.appendClassPath(jar.getAbsolutePath());
                    tmpJars.add(jar);
                }
            else {
                if (path.endsWith(File.pathSeparator))
                    path = path.substring(0, path.length() - File.pathSeparator.length());
                classPool.appendClassPath(path);
            }
    }

    private static String getExtension(String path) {
        int idx = path.lastIndexOf('.');
        return idx < 0 ? "": path.substring(idx).toLowerCase();
    }

    private void handleMethods(CtClass clsDest, ClassPool pool, String destClassName,
                               Map<String, Map<String, List<String[]>>> methods) throws NotFoundException {
        if (methods.isEmpty()) return;

        for (String className: methods.keySet()) {
            CtClass clsSrc = pool.getOrNull(className);
            if (clsSrc == null) {
                // hack for weaving demo in the default weaver.config
                if (!(className.startsWith("akha.yakhont.demoservice.MainService") ||
                      className.startsWith("akha.yakhont.demosimplekotlin.MainActivity")))
                    logWarning("can't find class '" + className + "'");
                continue;
            }

            if (clsDest.subclassOf(clsSrc)) {
                log(sNewLine + "class to weave: " + destClassName + " (based on " +
                        clsSrc.getName() + ")");
                for (String methodName: methods.get(className).keySet())
                    insertMethods(methods, clsDest, clsSrc, methodName, pool);
            }
        }
    }

    private void handleAnnotations(CtClass clsDest, ClassPool pool, boolean before)
            throws NotFoundException, CannotCompileException {
        if (mAnnotations.isEmpty()) return;

        List<String> names = new ArrayList<>(mAnnotations.keySet());
        if (before) Collections.reverse(names);

        for (int k = 0; k < names.size(); k++) {
            final CtMethod[] methods;
            try {
                methods = clsDest.getDeclaredMethods();
            }
            catch (Exception exception) {                   // API bug workaround
                String msg = exception.getMessage();
                if (msg != null && msg.contains("cannot find") && msg.contains("main.java.")) {
                    if (mDebug)
                        exception.printStackTrace();
                    else
                        logForce(msg);
                    return;
                }
                throw exception;
            }

            for (CtMethod method: methods) {
                if (!method.hasAnnotation(names.get(k))) continue;

                Map<Condition, List<String[]>> map = mAnnotations.get(names.get(k));

                List<Condition> conditions = new ArrayList<>(map.keySet());
                if (before) Collections.reverse(conditions);

                for (int j = 0; j < conditions.size(); j++) {
                    Condition condition = conditions.get(j);
                    if (!mDebugBuild && condition == Condition.DEBUG ||
                         mDebugBuild && condition == Condition.RELEASE) continue;

                    List<String[]> methodData = map.get(conditions.get(j));
                    if (before) Collections.reverse(methodData);

                    for (int i = 0; i < methodData.size(); i++)
                        weave(method, methodData.get(i), pool, before);
                }
            }
        }
    }

    private void validateAnnotations() {
        if (mAnnotations.isEmpty()) return;

        for (String name: mAnnotations.keySet()) {
            Map<Condition, List<String[]>> map = mAnnotations.get(name);

            List<String[]> validationData = new ArrayList<>();
            for (Condition condition: map.keySet())
                validationData.addAll(map.get(condition));

            validate(validationData, name);
        }
    }

    private static void validateMethods(Map<String, Map<String, List<String[]>>> methods) {
        if (methods.isEmpty()) return;

        for (String name: methods.keySet()) {
            Map<String, List<String[]>> map = methods.get(name);

            for (String method: map.keySet())
                validate(map.get(method), method);
        }
    }

    private static void validate(List<String[]> methodData, String name) {
        List<String> tmpData = new ArrayList<>(), tmpDescription = new ArrayList<>();

        for (String[] tmp: methodData)
            if (tmp[ACTION_METHOD] == null) {
                tmpData       .add(tmp[ACTION] + tmp[ACTION_TOKEN] + " " + name + " " + tmp[ACTION_CODE]      );
                tmpDescription.add(              tmp[ACTION_TOKEN] + " '"             + tmp[ACTION_CODE] + "'");
            }

        for (int i = 0; i < tmpData.size(); i++)
            if (Collections.frequency(tmpData, tmpData.get(i)) > 1)
                logError("duplicated entry for '" + name + "': " + tmpDescription.get(i));
    }

    private static String adjustMethodData(CtMethod method, String methodData) {
        String className = method.getDeclaringClass().getName();

        if (methodData.contains("$0") && Modifier.isStatic(method.getModifiers())) {
            log2ndPass(sNewLine, "for static method '" + method.getName() +
                    "' parameter '$0' in code to weave will be changed to the class '" + className + "'");
            methodData = methodData.replace("$0", className + ".class");
        }

        return methodData.replace(ALIAS_CLASS , "\"" + className        + "\"")
                         .replace(ALIAS_METHOD, "\"" + method.getName() + "\"");
    }

    private static boolean skipAction(String[] methodData, boolean before) {
        Action action = getAction(methodData);
        return !before && action == Action.INSERT_BEFORE ||
                before && action != Action.INSERT_BEFORE;
    }

    private void weave(CtMethod method, String[] methodData, ClassPool classPool, boolean before)
            throws NotFoundException, CannotCompileException {
        if (skipAction(methodData, before)) return;

        Action action     = getAction(methodData);
        String actionCode = adjustMethodData(method, methodData[ACTION_CODE]);

        log(sNewLine + "method " + method.getLongName() +
                " is already overridden; about to weave, action: " +
                getActionDescription(methodData) + ", code: " + actionCode);

        switch (action) {
            case INSERT_BEFORE:
                method.insertBefore(actionCode);
                break;
            case INSERT_AFTER:
                method.insertAfter (actionCode);
                break;
            case INSERT_AFTER_FINALLY:
                method.insertAfter (actionCode, true);
                break;
            case CATCH:
                method.addCatch    (actionCode, classPool.get(methodData[ACTION_TOKEN]), "$e");
                break;
            default:        // should never happen
                throw new CannotCompileException("error - unknown action: " + action);
        }
    }

    private void insertMethods(Map<String, Map<String, List<String[]>>> methods, CtClass clsDest,
                               CtClass clsSrc, String methodName, ClassPool pool) throws NotFoundException {
        List<String[]> methodData = methods.get(clsSrc.getName()).get(methodName);

        for (int i = methodData.size() - 1; i >= 0; i--)
            insertMethods(clsDest, clsSrc, methodName, pool, methodData.get(i), true);

        for (int i = 0; i < methodData.size(); i++)
            insertMethods(clsDest, clsSrc, methodName, pool, methodData.get(i), false);
    }

    private void insertMethods(CtClass clsDest, CtClass clsSrc, String method, ClassPool classPool,
                               String[] methodData, boolean before) throws NotFoundException {
        int posParenthesis = method.indexOf('(');
        String methodName = posParenthesis < 0 ? method: method.substring(0, posParenthesis);
        CtMethod[] methods = clsSrc.getDeclaredMethods(methodName);

        boolean isNew = false;
        if (methods.length == 0) {
            List<CtMethod> list = new ArrayList<>();
            for (CtMethod tmpMethod: clsSrc.getMethods())
                if (tmpMethod.getName().equals(methodName)) list.add(tmpMethod);

            methods = list.toArray(new CtMethod[0]);
        }
        if (methods.length == 0 && before) {
            String newMethod = methodData[ACTION_METHOD] == null ?
                    "public void " + methodName + "() {}": methodData[ACTION_METHOD];
            log2ndPass(sNewLine, "new method '" + newMethod.substring(0, newMethod
                    .indexOf('{')).replace(" (", "(").trim() +
                    "' will be created in the class '" + clsSrc.getName() + "'");
            try {
                methods = new CtMethod[] {CtNewMethod.make(newMethod, clsSrc)};
            }
            catch (CannotCompileException exception) {
                logWarning(exception.getMessage());
            }
            isNew = true;
        }
        if (methods.length == 0) return;

        boolean ignoreSignature = method.endsWith(IGNORE_SIGNATURE);
        if (ignoreSignature) posParenthesis = -1;
        if (methods.length > 1 && posParenthesis < 0 && !ignoreSignature)
            logError("there're several methods '" + methodName +
                    "', please specify signature or use '" + IGNORE_SIGNATURE + "'");

        for (CtMethod methodSrc: methods) {
            if (posParenthesis >= 0 && !methodSrc.getSignature().startsWith(
                    method.substring(posParenthesis))) continue;

            CtMethod methodDest = null;
            try {
                methodDest = clsDest.getDeclaredMethod(methodSrc.getName(), methodSrc.getParameterTypes());
            }
            catch (NotFoundException ignored) {     // sometimes swallowing Exceptions is acceptable
            }

            try {
                if (methodDest != null)
                    weave(methodDest, methodData, classPool, before);

                else {
                    if (skipAction(methodData, before)) continue;

                    String newMethod = getNewMethod(classPool, methodSrc, methodData, clsDest, isNew);
                    log(sNewLine + "about to add method " + method + sNewLine +
                            " method body: " + newMethod);
                    clsDest.addMethod(CtNewMethod.make(newMethod, clsDest));
                }
            }
            catch (CannotCompileException exception) {
                String message = exception.getMessage();
                // hack for weaving demo in the default weaver.config
                if (!(message.contains("no such class: akha.yakhont.demos")))
                    logWarning(message);
            }
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "UnusedParameters"})
    protected String getNewMethod(ClassPool classPool, CtMethod methodSrc, String[] data, CtClass clsDest,
                                  boolean isNew) throws NotFoundException, CannotCompileException {
        int modifiers = methodSrc.getModifiers();
        if (!isNew && (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers)))
            throw new CannotCompileException("error - can not override method '" + methodSrc.getName() +
                    "' 'cause it's static (or final / private)");

        String modifiersList = "";
        if (Modifier.isPublic      (modifiers)) modifiersList  = "public "      ;
        if (Modifier.isProtected   (modifiers)) modifiersList  = "protected "   ;
        if (Modifier.isPrivate     (modifiers)) modifiersList  = "private "     ;
        if (Modifier.isStatic      (modifiers)) modifiersList += "static "      ;
        if (Modifier.isFinal       (modifiers)) modifiersList += "final "       ;
        if (Modifier.isSynchronized(modifiers)) modifiersList += "synchronized ";
        if (Modifier.isStrict      (modifiers)) modifiersList += "strictfp"     ;

        CtClass[] paramTypes = methodSrc.getParameterTypes(), exceptionTypes = methodSrc.getExceptionTypes();
        if (paramTypes == null) paramTypes = new CtClass[] {};

        CtClass returnType   = methodSrc.getReturnType();
        boolean isVoid       = returnType.equals(CtClass.voidType);
        String  returnName   = returnType.getName().replace('$', '.');

        Action  action       = getAction(data);
        boolean hasTry       = action.equals(Action.INSERT_AFTER_FINALLY) || action.equals(Action.CATCH);
        Locale  locale       = Locale.getDefault();
        String  code         = adjustMethodData(methodSrc, data[ACTION_CODE]);

        StringBuilder pArgs  = new StringBuilder(), eArgs = new StringBuilder();
        for (int i = 0; i < paramTypes.length; i++)
            pArgs.append(String.format(locale, "%s%s %s%d", i == 0 ? "":
                    ", ", paramTypes[i].getName().replace('$', '.'), NM_ARG_PREFIX, i));
        for (int i = paramTypes.length; i > 0; i--)
            code = code.replace("$" + i, NM_ARG_PREFIX + (i - 1));

        for (int i = 0; i < exceptionTypes.length; i++)
            eArgs.append(String.format(locale, "%s %s", i == 0 ? "throws": ",",
                    exceptionTypes[i].getName().replace('$', '.')));

        String methodName = methodSrc.getName();
        return removeExtraSpaces(String.format("%s %s %s(%s) %s { %s %s %s" +
                        (isNew ? "%s": " super.%s($$);") + " %s %s %s }",
                modifiersList, returnName, methodName, pArgs, eArgs, hasTry ? "try { ": "",
                action.equals(Action.INSERT_BEFORE) ? code: "", isVoid || isNew ? "":
                        String.format("%s %s = ", returnName, NM_RESULT_NAME), isNew ? "": methodName,
                action.equals(Action.INSERT_AFTER)  ? code: "", isVoid || isNew ? "":
                        String.format("return %s;", NM_RESULT_NAME),
                !hasTry ? "": action.equals(Action.INSERT_AFTER_FINALLY) ? String.format(
                        " } finally { %s }", code): String.format(" } catch(%s %s) { %s }",
                        data[ACTION_TOKEN], NM_EXCEPTION_NAME,
                        code.replace("$e", NM_EXCEPTION_NAME))));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static void delete(String file) {
        if (file != null) delete(new File(file));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean delete(File file) {
        if (file == null) return true;

        boolean result = true;
        if (!file.exists()) //noinspection ConstantConditions
            return result;

        if (file.isDirectory()) {
            File[] files = file.listFiles();

            if (files != null)
                for (File tmpFile: files) {
                    boolean tmpResult = delete(tmpFile);
                    if (result) result = tmpResult;
                }
        }

        if (!file.delete()) {
            logError("can't delete file '" + file.getAbsolutePath() + "'");
            result = false;
        }
        return result;
    }

    private static List<String> read(String file) throws IOException {
        return read(new File(file));
    }

    private static List<String> read(File file) throws IOException {
        return file.exists() ? read(new FileReader(file)): Collections.emptyList();
    }

    private static List<String> read(Reader reader) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null)
                result.add(line);
        }
        return result;
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    private static boolean rename(File source, String destination) {
        try {
            Path sourcePath = source.toPath();
            Files.move(sourcePath, sourcePath.resolveSibling(destination));
            return true;
        }
        catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static boolean write(String file, String line) {
        try {
            Files.write(Paths.get(file), Collections.singletonList(line),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        }
        catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class WildCardsHandler {

        private static final String[]   ANNOTATIONS_SUFFIXES = new String[] {".",
                                                                             "." + CONDITION_DEBUG,
                                                                             "." + CONDITION_RELEASE};
        private static final String     URL_PREFIX           = "file:///"   ;
        private static final String     MASK_ALL_PACKAGES    = "**"         ;

        private        final List<File> mTmpJars             = new ArrayList<>();
        private        final List<URL > mUrls                = new ArrayList<>();

        private        final boolean    mDebug;

        public WildCardsHandler(boolean debug) {
            mDebug = debug;
        }

        public static boolean matches(String pattern, String string) {
            return hasWildCards(pattern) ?
                    Pattern.matches(wildcardToRegex(pattern), string): pattern.equals(string);
        }

        // based on https://www.rgagnon.com/javadetails/java-0515.html
        private static String wildcardToRegex(String wildcard) {
            StringBuilder builder = new StringBuilder("^");

            for (int i = 0; i < wildcard.length(); i++) {
                char ch = wildcard.charAt(i);

                switch (ch) {
                    case '*':
                        builder.append(".*");
                        break;
                    case '?':
                        builder.append('.');
                        break;

                    case '(': case ')': case '[': case ']': case '$': case '^': case '.':
                    case '{': case '}': case '|': case '\\':
                        builder.append('\\');                   // fall through

                    default:
                        builder.append(ch);
                        break;
                }
            }
            builder.append('$');

            return builder.toString();
        }

        public static boolean matchesClass(String pattern, String className) {
            int allPackagesPos = pattern.indexOf(MASK_ALL_PACKAGES);
            if (!validate(pattern, allPackagesPos)) return false;

            for (String suffix: ANNOTATIONS_SUFFIXES)
                if (pattern.endsWith(suffix)) {
                    pattern = pattern.substring(0, pattern.length() - suffix.length());
                    break;
                }

            if (allPackagesPos >= 0) pattern = pattern.replace(MASK_ALL_PACKAGES, "*");

            List<String> patternList = split(pattern  );
            List<String>    nameList = split(className);
            if (patternList == null || nameList == null) return false;

            if (allPackagesPos < 0)
                return patternList.size() == nameList.size() && check(patternList, nameList, 0);

            if (patternList.size() > nameList.size()) return false;

            int morePackagesPos = pattern.indexOf('.', allPackagesPos + 1);
            if (morePackagesPos <= 0) return check(patternList, nameList, 0);

            int lastSize = split(pattern.substring(morePackagesPos + 1)).size();
            if (!check(patternList, nameList, lastSize)) return false;

            for (int i = 0; i < lastSize; i++)
                if (!matches(patternList.get(patternList.size() - lastSize + i),
                                nameList.get(   nameList.size() - lastSize + i))) return false;
            return true;
        }

        private static boolean check(List<String> patternList, List<String> nameList, int delta) {
            for (int i = 0; i < patternList.size() - delta; i++)
                if (!matches(patternList.get(i), nameList.get(i))) return false;
            return true;
        }

        private static boolean validate(String pattern, int allPackagesPos) {
            if (allPackagesPos < 0) return true;

            if (pattern.indexOf(MASK_ALL_PACKAGES, allPackagesPos + 1) > 0) {
                errorReport(pattern, "'" + MASK_ALL_PACKAGES + "' can be used only once");
                return false;
            }
            if (pattern.trim().endsWith(MASK_ALL_PACKAGES + ".")) {
                errorReport(pattern, "after last '.' should be more names");
                return false;
            }
            if (pattern.length() > allPackagesPos + MASK_ALL_PACKAGES.length()  &&
                    pattern.charAt(allPackagesPos + MASK_ALL_PACKAGES.length()) != '.') {
                errorReport(pattern, "after '" + MASK_ALL_PACKAGES +
                        "' should be '.' for more names - or nothing");
                return false;
            }
            return true;
        }

        private static void errorReport(String pattern, String message) {
            logError("wrong pattern '" + pattern + "': " + message);
        }

        public static List<String> split(String src) {
            return split(src, ".");
        }

        public static List<String> split(String src, String delimiter) {
            List<String>  list = new ArrayList<>();
            List<String> empty = Collections.emptyList();

            int delimiterLength = delimiter.length();
            String tmp = src;
            for (;;) {
                int pos = tmp.indexOf(delimiter);
                if (pos >= 0) {
                    if (addNok(list, tmp.substring(0, pos), src)) return empty;
                    tmp = tmp.substring(pos + delimiterLength);
                }
                else {
                    if (addNok(list, tmp, src)) return empty;
                    break;
                }
            }
            return list;
        }

        private static boolean addNok(List<String> list, String string, String stringForErrorReport) {
            string = string.trim();
            if (string.length() > 0) {
                list.add(string);
                return false;
            }
            else {
                logError("wrong string '" + stringForErrorReport.trim() + "'");
                return true;
            }
        }

        @SuppressWarnings("UnstableApiUsage")
        public static Class<?> getClass(ClassInfo classInfo) {
            Class<?> cls = null;
            try {
                cls = classInfo.load();
            }
            catch (Throwable throwable) {
                logError("can't load class '" + classInfo.getName() + "'");
            }
            return cls;
        }

        @SuppressWarnings("UnstableApiUsage")
        public List<Class<?>> getDeclaredClassesAll(List<String> patterns, List<ClassInfo> classInfo) {
            List<Class<?>> allClasses = new ArrayList<>();
            for (ClassInfo info: classInfo) {
                Class<?> cls = getClass(info);
                if (cls != null) allClasses.add(cls);
            }
            return getDeclaredClasses(patterns, allClasses);
        }

        public List<Class<?>> getDeclaredClasses(List<String> patterns, List<Class<?>> classes) {
            List<Class<?>> result = classes;
            for (int i = 0; i < patterns.size(); i++)
                result = getDeclaredClasses(patterns.get(i), result);
            return result;
        }

        public List<Class<?>> getDeclaredClasses(String pattern, List<Class<?>> classes) {
            List<Class<?>> result = new ArrayList<>();
            for (Class<?> cls: classes)
                result.addAll(getDeclaredClasses(pattern, cls));
            return result;
        }

        public List<Class<?>> getDeclaredClasses(String patternClass, Class<?> cls) {
            if (cls == null) return Collections.emptyList();

            List<Class<?>> result = new ArrayList<>();
            try {
                for (Class<?> tmpClass: getDeclaredClasses(cls))
                    if (matches(patternClass, getName(tmpClass))) result.add(tmpClass);
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }

        private Collection<Class<?>> getDeclaredClasses(Class<?> cls) {
            Map<String, Class<?>> classes = new HashMap<>();

            Class<?> tmpClass = cls;
            //noinspection ConditionalBreakInInfiniteLoop
            for (;;) {
                for (Class<?> tmp: tmpClass.getDeclaredClasses()) {
                    if ((tmp.isAnonymousClass() || tmp.isInterface() ||
                         tmp.isPrimitive     () || tmp.isArray    ())) continue;

                    String key = getName(tmp);

                    if (classes.containsKey(key))
                        log(!mDebug, "for key '" + key + "' can't add the class '" + tmp + "'");
                    else
                        classes.put(key, tmp);
                }

                tmpClass = tmpClass.getSuperclass();
                if (tmpClass == null) break;
            }

            return classes.values();
        }

        private static String getName(Class<?> cls) {
            return cls.getSimpleName();
        }

        private Collection<Method> getDeclaredMethods(Class<?> cls) {
            Map<String, Method> methods = new HashMap<>();

            Class<?> tmpClass = cls;
            //noinspection ConditionalBreakInInfiniteLoop
            for (;;) {
                for (Method method: tmpClass.getDeclaredMethods()) {
                    String key = getMethodString(method.toString());

                    if (methods.containsKey(key))
                        log(!mDebug, "method is already added: '" + method + "' with key '" + key + "'");
                    else
                        methods.put(key, method);
                }

                tmpClass = tmpClass.getSuperclass();
                if (tmpClass == null) break;
            }

            return methods.values();
        }

        public List<String> getDeclaredMethods(String patternMethod, Class<?> cls) {
            if (cls == null) return Collections.emptyList();

            List<String> result = new ArrayList<>();
            try {
                for (Method method: getDeclaredMethods(cls)) {
                    String methodName = method.getName();

                    int posPatternParenthesis = patternMethod.indexOf('(');
                    if (posPatternParenthesis < 0) {
                        if (matches(patternMethod, methodName))
                            result.add(methodName);
                        continue;
                    }
                    if (!matches(patternMethod.substring(0, posPatternParenthesis), methodName)) continue;

                    String methodString = getMethodString(method.toString());
                    if (methodString == null) continue;

                    String methodArgs = methodString.substring(methodString.indexOf('('));

                    if (patternMethod.substring(posPatternParenthesis).equals(methodArgs))
                        result.add(methodName + methodArgs);
                }
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }

        private static void errorReport(String method) {
            logError("unsupported method description '" + method + "'");
        }

        public static String getMethodString(String method) {
            int posThrows      = method.toLowerCase().indexOf(") throws");
            if (posThrows > 0)   method = method.substring(0, posThrows + 1);

            int posDot         = method.indexOf('.');
            int posParenthesis = method.indexOf('(');
            if (posDot < 0 || posParenthesis < 0 || posDot > posParenthesis) {
                errorReport(method);
                return null;
            }
            int posResultSpace = method.substring(0, posParenthesis).lastIndexOf(' ');
            if (posResultSpace < 0) {
                errorReport(method);
                return null;
            }
            int posModifiersSpace = method.substring(0, posResultSpace).lastIndexOf(' ');

            if (posModifiersSpace >= 0) method = method.substring(posModifiersSpace + 1);

            posResultSpace = method.indexOf(' ');
            posDot         = getMethodDotPos(method);
            if (posDot < 0) {
                errorReport(method);
                return null;
            }
            return method.substring(0, posResultSpace + 1) + method.substring(posDot + 1);
        }

        public static int getMethodDotPos(String method) {
            int posParenthesis = method.indexOf('(');
            return posParenthesis >= 0 ? method.substring(0, posParenthesis).lastIndexOf('.'):
                    method.lastIndexOf('.');
        }

        @SuppressWarnings("UnstableApiUsage")
        public ClassPath getClassPath(ClassPool classPool) {
            if (mUrls.size() == 0) {
                logError("no classes for ClassPool (" + classPool + ")");
                return null;
            }

            ClassPath classPath = null;
            try {
                classPath = ClassPath.from(URLClassLoader.newInstance(
                        mUrls.toArray(new URL[0]), new Loader(classPool)));
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
            finally {
                mUrls.clear();

                for (File tmp: mTmpJars)
                    //noinspection CatchMayIgnoreException
                    try {
                        delete(tmp);
                    }
                    catch (Exception e) {}  // swallowing Exceptions is a bad practice - but not always :-)

                mTmpJars.clear();
            }

            return classPath;
        }

        private static String makeUrl(String classes) {
            return URL_PREFIX + classes.replace('\\', '/');
        }

        public void addClassPath(String classPath) {
            for (String classes: classPath.split(File.pathSeparator))
                add(classes);
        }

        public void add(String classes) {
            try {
                addHelper(classes);
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        private void addHelper(String classes) throws IOException {
            String classesLowerCase = classes.toLowerCase();

            if (classes.endsWith(File.separator) || classesLowerCase.endsWith(".jar")) {
                mUrls.add(new URL(makeUrl(classes)));
                return;
            }

            if (!classesLowerCase.endsWith(".aar")) {
                mUrls.add(new URL(makeUrl(classes + "/")));
                return;
            }

            for (File jar: new LibHandler(mDebug).getJars(classes, null)) {
                mTmpJars.add(jar);
                mUrls.add(new URL(makeUrl(jar.getCanonicalPath())));
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Helper class to work with JARs / AARs.
     */
    public static class LibHandler {

        private static final String                 LIBS                 = "libs/"   ;
        private static final String                 TMP_FILE_PREFIX      = "yakhont_";

        private static final int                    BUFFER_SIZE          = 8192      ;

        private static final Map<String, String>    sFileSystemEnv;
        private        final boolean                mDebug        ;

        static {
            sFileSystemEnv = new HashMap<>();
            sFileSystemEnv.put("create", "true");
        }

        /**
         * Initialises a newly created {@code LibHandler} object.
         *
         * @param debug
         *        The flag which switches ON / OFF printing debug messages to the console
         */
        public LibHandler(boolean debug) {
            mDebug = debug;
        }

        /**
         * Replaces file in the given JAR / AAR.
         *
         * @param file
         *        The JAR / AAR file to update
         *
         * @param entry
         *        The JAR / AAR entry name
         *
         * @param newEntry
         *        The new file to replace the old entry
         *
         * @return  {@code true} if file was successfully replaced, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public boolean replace(String file, String entry, String newEntry) {
            // based on https://gist.github.com/DataPools/9c66bec1c9de1bbb626137056d788fa7
            log(!mDebug, sNewLine + "about to update '" + file + "' with '" + entry +
                    "' from '" + newEntry + "'...");
            try {
                try (FileSystem fileSystem = FileSystems.newFileSystem(
                        URI.create("jar:" + new File(file).toURI()), sFileSystemEnv)) {
                    Files.copy(Paths.get(newEntry), fileSystem.getPath(entry),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                log(!mDebug, "'" + file + "' updated with '" + entry + "'");
                return true;
            }
            catch (/*IO*/Exception exception) {
                exception.printStackTrace();
                logError("failed to update '" + file + "' with '" + entry + "' from '" + newEntry + "'");
                return false;
            }
        }

        /**
         * Extracts JARs from the given AAR. Please refer to {@link #getJars(String, List)} for more info.
         *
         * @param aar
         *        The AAR file
         *
         * @param result
         *        The list to fill with resulting JARs (or null)
         *
         * @return  The list of resulting JARs
         */
        @SuppressWarnings({"unused", "RedundantSuppression"})
        public List<File> getJars(File aar, List<File> result) {
            return getJars(aar.getAbsolutePath(), result);
        }

        /**
         * Extracts JARs from the given AAR (all JARs and everything from 'libs', recursively).
         * These files are temporary ones, and it's a caller responsibility to remove them.
         *
         * @param aar
         *        The AAR file name
         *
         * @param result
         *        The list to fill with resulting JARs (or null)
         *
         * @return  The list of resulting JARs
         */
        public List<File> getJars(String aar, List<File> result) {
            if (result == null) result = new ArrayList<>();
            getJarsHelper(aar, result);
            return result;
        }

        private void getJarsHelper(String aar, List<File> result) {
            log(!mDebug, "about to handle jars in '" + aar + "'");
            List<File> tmpFiles = new ArrayList<>();

            //noinspection Convert2Lambda
            handleAar(aar, null, new AarEntryHandler() {
                @Override
                public void handle(List<String> internalPath, File file) {
                    log(!mDebug, "for aar '" + aar + "' found entry '" +
                            Arrays.deepToString(internalPath.toArray()) + "'");
                    result.add(file);
                }
            }, tmpFiles, mDebug);

            for (int i = tmpFiles.size() - 1; i >= 0; i--) {
                File tmpFile = tmpFiles.get(i);
                if (!result.contains(tmpFile)) delete(tmpFile);
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static List<File[]> getAarLibAsTmpFiles(File lib, List<String> entryLibs) throws IOException {
            List<File[]> result = new ArrayList<>();
            getAarLibAsTmpFiles(lib, entryLibs, result, true);
            return result;
        }

        private static void getAarLibAsTmpFiles(File lib, List<String> entryLibs, List<File[]> result,
                                                boolean firstPass) throws IOException {
            if (entryLibs.isEmpty()) {
                logError("empty lib entries to handle '" + lib.getAbsolutePath() + "'");
                return;
            }
            String entryLib = entryLibs.get(0);
            File[] tmp = firstPass && isAar(entryLib) ?
                    new File[] { new File(entryLib), null }: getAarLibAsTmpFile(lib, entryLib);
            if (tmp[0] == null) {
                logError("can't find tmp file for '" + entryLib + "'");
                return;
            }
            result.add(tmp);

            String path = tmp[0].getAbsolutePath();
            if (isAar(tmp[0].getName()))
                if (entryLibs.size() < 2)
                    logError("no lib entry to handle '" + path + "'");
                else
                    getAarLibAsTmpFiles(tmp[0], entryLibs.subList(1, entryLibs.size()),
                            result, false);
            else if (entryLibs.size() > 1)
                logError("too many lib entries for '" + path + "': " +
                        Arrays.deepToString(entryLibs.toArray()));
        }

        private static boolean isAar(String path) {
            return getExtension(path).equals(".aar");
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static File[] getAarLibAsTmpFile(File lib, String entryLib) throws IOException {
            File[] result = new File[] { null, Files.createTempDirectory(null).toFile() };
            File[] tmpDir = new File[] { result[1] };

            //noinspection Convert2Lambda
            zipEntryHandle(lib.getAbsolutePath(), new ZipEntryHandler() {
                @Override
                public void handle(ZipInputStream zipInputStream, ZipEntry zipEntry) {
                    String entryName = zipEntry.getName();
                    if (!entryName.equals(entryLib)) return;

                    String[] entryNames = entryName.split("/");
                    for (int i = 0; i < entryNames.length - 1; i++) {
                        tmpDir[0] = new File(tmpDir[0], entryNames[i]);
                        if (!tmpDir[0].mkdir())
                            logError("can't create dir '" + tmpDir[0] + "'");
                    }

                    try {
                        result[0] = new File(tmpDir[0], entryNames[entryNames.length - 1]);
                        if (!getZipEntry(zipInputStream, new FileOutputStream(result[0])))
                            result[0] = null;
                    }
                    catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            });

            return result;
        }

        private static void classMapHandle(BufferedWriter writer, File lib, boolean debug,
                                           boolean noJetified) {
            String path = lib.getAbsolutePath();

            List<String> pathList = new ArrayList<>();
            pathList.add(path);

            switch (getExtension(path)) {
                case ".jar":
                    if (noJetified) {
                        String jarName = getJarName(path);
                        if (isTmpJarFromAar(jarName) || isJetifiedJar(jarName)) {
                            log(!debug, "tmp JAR will be skipped in class map: " + lib);
                            break;
                        }
                    }
                    classMapHandleJar(writer, path, pathList);
                    break;

                case ".aar":
                    classMapHandleAar(writer, path, pathList, debug);
                    break;

                default:
                    logWarning("unknown lib: " + lib);
                    break;
            }
        }

        private static void classMapHandleAar(BufferedWriter writer, String path, List<String> pathList,
                                              boolean debug) {
            //noinspection Convert2Lambda
            handleAar(path, pathList, new AarEntryHandler() {
                @Override
                public void handle(List<String> innerPath, File file) {
                    classMapHandleJar(writer, file.getAbsolutePath(), innerPath);
                }
            }, null, debug);
        }

        private static void classMapHandleJar(BufferedWriter writer, String path, List<String> pathList) {
            //noinspection Convert2Lambda
            zipEntryHandle(path, new ZipEntryHandler() {
                @Override
                public void handle(ZipInputStream zipInputStream, ZipEntry zipEntry) {
                    String entryName = zipEntry.getName();
                    if (!entryName.toLowerCase().endsWith(".class")) return;

                    try {
                        StringBuilder fullPath = new StringBuilder(entryName);
                        for (int i = 0 ; i < pathList.size(); i++)
                            fullPath.append(File.pathSeparator).append(pathList.get(i));
                        writer.write(fullPath.toString());
                        writer.newLine();
                    }
                    catch (Exception exception) {
                        classMapHandleError(exception);
                    }
                }
            });
        }

        /**
         * Handles JARs in AAR, recursively.
         *
         * @param aar
         *        The AAR to handle
         *
         * @param innerPath
         *        The list of files in AAR which contains the given JAR
         *
         * @param handler
         *        The JAR's handler
         *
         * @param tmpFiles
         *        The list of tmp JAR / AAR files; if null, they will be deleted by Yakhont,
         *        otherwise should be deleted by the caller
         *
         * @param debug
         *        The flag which switches ON / OFF printing debug messages to the console
         */
        public static void handleAar(String aar, List<String> innerPath, AarEntryHandler handler,
                                     List<File> tmpFiles, boolean debug) {
            //noinspection Convert2Lambda
            zipEntryHandle(aar, new ZipEntryHandler() {
                @Override
                public void handle(ZipInputStream zipInputStream, ZipEntry zipEntry) {
                    String entryName = zipEntry.getName(), entryNameLower = entryName.toLowerCase();
                    String extension = getExtension(entryName);

                    if (!extension.equals(".jar") && !extension.equals(".aar")) return;

                    List<String> tmpPath =
                            innerPath == null ? new ArrayList<>(): new ArrayList<>(innerPath);
                    tmpPath.add(entryName);

                    File tmpFile = new LibHandler(debug).getZipEntry(zipInputStream,
                            entryName, entryName.substring(entryName.length() - 3));

                    if (extension.equals(".jar"))
                        try {
                            handler.handle(tmpPath, tmpFile);
                        }
                        catch (Exception exception) {
                            exception.printStackTrace();
                        }

                    if (tmpFile != null) {
                        if (extension.equals(".aar") && entryNameLower.startsWith(LIBS))
                            handleAar(tmpFile.getAbsolutePath(), tmpPath, handler, tmpFiles, debug);

                        if (tmpFiles == null)
                            delete(tmpFile);
                        else
                            tmpFiles.add(tmpFile);
                    }
                }
            });
        }

        /**
         * API to handle JARs in AAR.
         */
        public interface AarEntryHandler {

            /**
             * JAR handler.
             *
             * @param innerPath
             *        The list of files in AAR which contains the given JAR
             *
             * @param tmpJar
             *        The extracted temp JAR file
             */
            void handle(List<String> innerPath, File tmpJar);
        }

        private interface ZipEntryHandler {
            void handle(ZipInputStream zipInputStream, ZipEntry zipEntry);
        }

        private static void zipEntryHandle(String zip, ZipEntryHandler handler) {
            ZipInputStream zipInputStream;
            try {
                zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
            }
            catch (FileNotFoundException exception) {
                logWarning("can't find '" + zip + "'");
                return;
            }

            try {
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null)
                    handler.handle(zipInputStream, zipEntry);
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
            finally {
                try {
                    zipInputStream.close();
                }
                catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

        private File getZipEntry(ZipInputStream zipInputStream, String entryName, String extension) {
            try {
                File tmpFile = File.createTempFile(TMP_FILE_PREFIX, "." + extension);

                if (getZipEntry(zipInputStream, new FileOutputStream(tmpFile))) {
                    log(!mDebug, "created '" + tmpFile.getCanonicalPath() +
                            "' for '" + entryName + "'");
                    return tmpFile;
                }

                delete(tmpFile);
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
            return null;
        }

        private static boolean getZipEntry(ZipInputStream zipInputStream, OutputStream outputStream) {
            boolean[] result = new boolean[] { true };
            byte   [] buffer = new byte[BUFFER_SIZE];
            int       length;

            try {
                while ((length = zipInputStream.read(buffer)) != -1)
                    outputStream.write(buffer, 0, length);
            }
            catch (Exception exception) {
                handleZipError(result, exception);
            }
            finally {
                try {
                    outputStream.close();
                }
                catch (Exception exception) {
                    handleZipError(result, exception);
                }
            }

            return result[0];
        }

        private static void handleZipError(boolean[] result, Exception exception) {
            result[0] = false;
            exception.printStackTrace();
        }
    }
}
