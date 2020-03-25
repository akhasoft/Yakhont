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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * The <code>Weaver</code> class weaves the given application's compiled classes (both Java and Kotlin)
 * according to the configuration file info; supports both explicit method callbacks declarations and
 * declarations via annotations (which defines callbacks for the annotated methods).
 * <br>For more info please refer to the 'weaver.config' configuration file.
 *
 * <p>Weaver supports any applications (i.e. you can use Weaver without Yakhont library).
 *
 * @author akha
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class Weaver {

    private static final String COMMENT             = "#" ;
    private static final String DELIMITER           = " " ;
    private static final String EXCEPTION_NAME      = "e" ;
    private static final String IGNORE_SIGNATURE    = "()";

    private static final String CONDITION_DEBUG     = "_D";
    private static final String CONDITION_RELEASE   = "_R";

    private static final String DEF_CONFIG          = "weaver.config";

    private static final String ERROR               = "  *** Yakhont weaver error: ";
    private static final String WARNING             = "  *** Yakhont weaver warning: ";

    private static final String sNewLine            = System.getProperty("line.separator");

    private        final Map<String, Map<String,    List<String>>>
                                mMethodsToWeave     = new LinkedHashMap<>();
    private        final Map<String, Map<Condition, List<String>>>
                                mAnnotations        = new LinkedHashMap<>();

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

    private String  mPackageName, mClassPath, mBootClassPath;
    private boolean mDebug, mDebugBuild, mUpdated;

    /**
     * Initialises a newly created {@code Weaver} object.
     */
    public Weaver() {
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
    protected void log(boolean silent, String message) {
        if (!silent) System.out.println(message);
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
    public void run(boolean debug, String packageName, String classesDir,
                    String classPath, String bootClassPath, boolean addConfig, String... configFiles)
            throws NotFoundException, CannotCompileException, IOException {
        if (classesDir.endsWith("debug"))
            run(true, debug, packageName, classesDir, classPath, bootClassPath,
                    addConfig, configFiles);
        else if (classesDir.endsWith("release"))
            run(false, debug, packageName, classesDir, classPath, bootClassPath,
                    addConfig, configFiles);
        else
            log(false, ERROR + "can not detect build type");
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
    public void run(boolean debugBuild, boolean debug, String packageName, String classesDirs,
                    String classPath, String bootClassPath, boolean addConfig, String... configFiles)
            throws NotFoundException, CannotCompileException, IOException {

        log(false, sNewLine + "Yakhont: weaving classes in [" + classesDirs + "]...");

        try {
            run(debugBuild, debug, packageName, classesDirs, classPath, bootClassPath, configFiles, addConfig);
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

        mPackageName   = packageName;
        mClassPath     = classPath;
        mBootClassPath = bootClassPath;

        log(sNewLine + "classpath:");
        for (String token: classPath    .split(File.pathSeparator)) log(token);
        log(sNewLine + "boot classpath:");
        for (String token: bootClassPath.split(File.pathSeparator)) log(token);

        log(sNewLine + "package name: " + packageName);

        mAnnotations   .clear();
        mMethodsToWeave.clear();

        if (addConfig) parseConfig();
        if (configFiles != null) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < configFiles.length; i++)
                parseConfig(configFiles[i]);
        }
        printConfig(mMethodsToWeave, mAnnotations);

        validateMethods();
        validateAnnotations();

        for (String classesDir: classesDirs.split(File.pathSeparator)) {
            log(sNewLine + sNewLine + "-- about to weave classes in [" + classesDir + "]" + sNewLine);
            searchClasses(new File(classesDir).listFiles());
        }

        if (mMethodsToWeave.size() + mAnnotations.size() > 0 && !mUpdated)
            log(false, sNewLine + WARNING + "no classes to weave");
    }

    private void parseConfig() throws IOException, CannotCompileException {
        log(sNewLine + sNewLine + "config file: default");
        String line;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(DEF_CONFIG))))) {
            while ((line = in.readLine()) != null)
                parseConfig(mMethodsToWeave, mAnnotations, line);
        }
    }

    private void parseConfig(String configFile) throws IOException, CannotCompileException {
        if (configFile == null) return;
        configFile = configFile.replace("\\", File.separator)
                .replace("/", File.separator);
        log(sNewLine + sNewLine + "config file: " + configFile);

        for (String line: Files.readAllLines(Paths.get(configFile), Charset.defaultCharset()))
            parseConfig(mMethodsToWeave, mAnnotations, line);
    }

    /**
     * Parses the configuration file.
     *
     * @param mapM
     *        The internal representation of methods configuration info
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
    protected void parseConfig(Map<String, Map<String   , List<String>>> mapM,
                               Map<String, Map<Condition, List<String>>> mapA,
                               String line) throws CannotCompileException {
        if (line          == null) return;      // should never happen
        line = line.trim();
        if (line.length() ==    0) return;

        int idx = line.indexOf(COMMENT);
        if (idx == 0) return;
        if (idx >  0) line = line.substring(0, idx);

        List<String> tokens = new LinkedList<>();
        for (Matcher m = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'").matcher(line.trim()); m.find();)
            tokens.add(m.group().replace("'", ""));

        if (tokens.size() == 0) return;
        if (tokens.size() <  3) throw new CannotCompileException("error - invalid line: " + line);

        Action action;
        String actionToken = tokens.get(1);
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

        String methodName = tokens.get(0).substring(tokens.get(0).lastIndexOf(".") + 1);
        String  className = tokens.get(0).substring(0, tokens.get(0).length() - methodName.length() - 1);

        if (methodName.length() == 0 ||
                methodName.equals(CONDITION_RELEASE) || methodName.equals(CONDITION_DEBUG)) {
            Condition condition;

            switch (methodName.toUpperCase()) {
                case CONDITION_RELEASE:
                    condition = Condition.RELEASE;
                    break;
                case CONDITION_DEBUG:
                    condition = Condition.DEBUG;
                    break;
                default:        // fall through
                    log(false, ERROR + "unknown annotation qualifier: " + methodName);
                case "":
                    condition = Condition.NOT_DEFINED;
            }
            put(className,  condition, action, actionToken, tokens, mapA);
        }
        else
            put(className, methodName, action, actionToken, tokens, mapM);
    }

    private <T> void put(String className, T key, Action action, String actionToken,
                         List<String> tokens, Map<String, Map<T, List<String>>> map) {
        if (!map.containsKey(className)) //noinspection Convert2Diamond
            map.put(className, new LinkedHashMap<T, List<String>>());
        Map<T, List<String>> classMap = map.get(className);

        if (!classMap.containsKey(key))  //noinspection Convert2Diamond
            classMap.put(key, new ArrayList<String>());
        classMap.get(key).add(action.ordinal() + actionToken +
                DELIMITER + removeExtraSpaces(tokens.get(2)));
    }

    private <T> void print(Map<String, Map<T, List<String>>> map, String title) {
        if (map.isEmpty()) return;
        log(sNewLine + "- " + title);
        for (String key: map.keySet()) {
            log(sNewLine + "--- " + key);
            for (T subKey: map.get(key).keySet()) {
                log(sNewLine + "   " + subKey);
                for (String methodData: map.get(key).get(subKey))
                    log("    action: " + getActionDescription(methodData) + ", code: '" +
                            getCode(methodData) + "'");
            }
        }
    }

    private void printConfig(Map<String, Map<String   , List<String>>> mapM,
                             Map<String, Map<Condition, List<String>>> mapA) {
        log(sNewLine + "START OF CONFIG");
        if (mapA.isEmpty()) log(sNewLine + "No annotations defined");
        if (mapM.isEmpty()) log(sNewLine + "No methods defined");
        print(mapA, "ANNOTATIONS");
        print(mapM, "METHODS");
        log(sNewLine + "END OF CONFIG");
    }

    private String removeExtraSpaces(String str) {
        return str.replaceAll("\\s+", " ");
    }

    private Action getAction(String methodData) {
        return Action.values()[Integer.parseInt(methodData.substring(0, 1))];
    }

    private String getActionDescription(String methodData) {
        Action action = getAction(methodData);
        return (action.equals(Action.CATCH) ? "catch ": "") + getActionDescriptionRaw(methodData);
    }

    private String getActionDescriptionRaw(String methodData) {
        return methodData.substring(1, methodData.indexOf(DELIMITER));
    }

    private String getCode(String methodData) {
        return methodData.substring(methodData.indexOf(DELIMITER) + 1);
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

        int idx = path.indexOf(mPackageName.replace(".", File.separator));
        if (idx < 0) return;

        destClassName  = path.substring(idx).replace(File.separator, ".")
                .substring(0, path.length() - idx - 6);     // remove '.class'
        String rootDir = path.substring(0, idx);

        // ClassPool.getDefault() returns singleton but we need new instance
        ClassPool pool = new ClassPool(true);
        pool.insertClassPath(rootDir.endsWith(File.separator) ?
                rootDir.substring(0, rootDir.length() - 1): rootDir);

        pool.appendPathList(mClassPath);
        pool.appendPathList(mBootClassPath);

        CtClass clsDest = pool.get(destClassName);
        if (!clsDest.getPackageName().startsWith(mPackageName)) return;

        handleAnnotations(clsDest, pool, true);
        handleMethods    (clsDest, pool, destClassName);
        handleAnnotations(clsDest, pool, false);

        if (!clsDest.isModified()) return;

        log(sNewLine + "about to write class " + clsDest.getName() + " to " + rootDir);
        clsDest.writeFile(rootDir.endsWith(File.separator) ? rootDir: rootDir + File.separator);

        mUpdated = true;
    }

    private void handleMethods(CtClass clsDest, ClassPool pool, String destClassName)
            throws NotFoundException, CannotCompileException {
        if (mMethodsToWeave.isEmpty()) return;

        for (String className: mMethodsToWeave.keySet()) {
            CtClass clsSrc = pool.getOrNull(className);
            if (clsSrc == null) throw new CannotCompileException(
                    "error - can not find class: " + className);

            if (clsDest.subclassOf(clsSrc)) {
                log(sNewLine + "--- class to weave: " + destClassName + " (based on " +
                        clsSrc.getName() + ")");
                for (String methodName: mMethodsToWeave.get(className).keySet())
                    insertMethods(clsDest, clsSrc, methodName, pool);
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
                        log(false, msg);
                    return;
                }
                throw exception;
            }

            for (CtMethod method: methods) {
                if (!method.hasAnnotation(names.get(k))) continue;

                Map<Condition, List<String>> map = mAnnotations.get(names.get(k));

                List<Condition> conditions = new ArrayList<>(map.keySet());
                if (before) Collections.reverse(conditions);

                for (int j = 0; j < conditions.size(); j++) {
                    Condition condition = conditions.get(j);
                    if (!mDebugBuild && condition == Condition.DEBUG ||
                            mDebugBuild && condition == Condition.RELEASE) continue;

                    List<String> methodData = map.get(conditions.get(j));
                    if (before) Collections.reverse(methodData);

                    for (int i = 0; i < methodData.size(); i++)
                        weave(method, methodData.get(i).replace(
                                "$proceed", "\"" + method.getName() + "\""),
                                pool, before);
                }
            }
        }
    }

    private void validateAnnotations() {
        if (mAnnotations.isEmpty()) return;

        for (String name: mAnnotations.keySet()) {
            Map<Condition, List<String>> map = mAnnotations.get(name);

            List<String> validationData = new ArrayList<>();
            for (Condition condition: map.keySet())
                validationData.addAll(map.get(condition));

            validate(validationData, name);
        }
    }

    private void validateMethods() {
        if (mMethodsToWeave.isEmpty()) return;

        for (String name: mMethodsToWeave.keySet()) {
            Map<String, List<String>> map = mMethodsToWeave.get(name);

            for (String method: map.keySet())
                validate(map.get(method), method);
        }
    }

    private void validate(List<String> methodData, String name) {
        for (String data: methodData)
            if (Collections.frequency(methodData, data) > 1)
                log(false, ERROR + "duplicated entries for " + name + ": " + getCode(data));
    }

    private void weave(CtMethod method, String methodData, ClassPool classPool, boolean before)
            throws NotFoundException, CannotCompileException {
        Action action = getAction(methodData);
        if (!before && action == Action.INSERT_BEFORE ||
             before && action != Action.INSERT_BEFORE) return;

        log(sNewLine + "method " + method.getLongName() +
                " is already overridden; about to weave, action: " +
                getActionDescription(methodData) + ", code: " + getCode(methodData));

        switch (action) {
            case INSERT_BEFORE:
                method.insertBefore(getCode(methodData));
                break;
            case INSERT_AFTER:
                method.insertAfter(getCode(methodData));
                break;
            case INSERT_AFTER_FINALLY:
                method.insertAfter(getCode(methodData), true);
                break;
            case CATCH:
                method.addCatch(getCode(methodData),
                        classPool.get(getActionDescriptionRaw(methodData)),
                        "$" + EXCEPTION_NAME);
                break;
            default:        // should never happen
                throw new CannotCompileException("error - unknown action: " + action);
        }
    }

    private void insertMethods(CtClass clsDest, CtClass clsSrc, String methodName, ClassPool pool)
            throws NotFoundException, CannotCompileException {
        List<String> methodData = mMethodsToWeave.get(clsSrc.getName()).get(methodName);

        for (int i = methodData.size() - 1; i >= 0; i--)
            insertMethods(clsDest, clsSrc, methodName, pool, methodData.get(i), true);

        for (int i = 0; i < methodData.size(); i++)
            insertMethods(clsDest, clsSrc, methodName, pool, methodData.get(i), false);
    }

    private void insertMethods(CtClass clsDest, CtClass clsSrc, String methodName,
                               ClassPool classPool, String methodData, boolean before)
            throws NotFoundException, CannotCompileException {

        int idx = methodName.indexOf('(');
        CtMethod[] methods = clsSrc.getDeclaredMethods(idx < 0 ? methodName:
                methodName.substring(0, idx));

        boolean ignoreSignature = methodName.endsWith(IGNORE_SIGNATURE);
        if (ignoreSignature) idx = -1;
        if (methods.length > 1 && idx < 0 && !ignoreSignature)
            log(false, ERROR + "there're several methods " + methodName +
                    ", please specify signature or use " + IGNORE_SIGNATURE);

        for (CtMethod methodSrc: methods) {
            if (idx >= 0 && !methodSrc.getSignature().startsWith(methodName.substring(idx))) continue;

            CtMethod methodDest = null;
            try {
                methodDest = clsDest.getDeclaredMethod(methodSrc.getName(), methodSrc.getParameterTypes());
            }
            catch (NotFoundException ignored) {
            }

            if (methodDest != null)
                weave(methodDest, methodData, classPool, before);
            else {
                String newMethod = getNewMethod(classPool, methodSrc, methodData, clsDest);
                log(sNewLine + "about to add method " + methodName + sNewLine +
                        " method body: " + newMethod);
                clsDest.addMethod(CtNewMethod.make(newMethod, clsDest));
            }
        }
    }

    /**
     * Creates a new overriding method in the destination class.
     *
     * @param classPool
     *        The ClassPool
     *
     * @param methodSrc
     *        The method to override
     *
     * @param data
     *        The code to weave
     *
     * @param clsDest
     *        The destination class
     *
     * @return  The {@code String} containing the Java code of the created method
     *
     * @throws  NotFoundException
     *          please refer to the exception description
     * @throws  CannotCompileException
     *          please refer to the exception description
     */
    @SuppressWarnings({"WeakerAccess", "UnusedParameters"})
    protected String getNewMethod(ClassPool classPool, CtMethod methodSrc, String data, CtClass clsDest)
            throws NotFoundException, CannotCompileException {
        // it seems CtNewMethod.delegator(...) is not applicable here

        int modifiers = methodSrc.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers))
            throw new CannotCompileException("error - can not override " + methodSrc.getName() +
                    " 'cause it's static (or final / private)");
        String modifiersList = "";
        if (Modifier.isSynchronized(modifiers)) modifiersList += "synchronized ";
        if (Modifier.isStrict      (modifiers)) modifiersList += "strictfp";

        CtClass[] paramTypes = methodSrc.getParameterTypes(), exceptionTypes = methodSrc.getExceptionTypes();
        if (paramTypes == null) paramTypes = new CtClass[] {};

        CtClass returnType = methodSrc.getReturnType();
        boolean isVoid = returnType.equals(CtClass.voidType);
        String returnName = returnType.getName().replace('$', '.');

        Action action = getAction(data);
        boolean isTry  = action.equals(Action.INSERT_AFTER_FINALLY) || action.equals(Action.CATCH);

        String argPrefix = "arg", code = getCode(data);
        StringBuilder pArgs = new StringBuilder(), eArgs = new StringBuilder();
        for (int i = 0; i < paramTypes.length; i++)
            pArgs.append(String.format(Locale.getDefault(), "%s%s %s%d", i == 0 ? "":
                    ", ", paramTypes[i].getName().replace('$', '.'), argPrefix, i + 1));
        for (int i = paramTypes.length; i > 0; i--)
            code = code.replace("$" + i, argPrefix + i);

        for (int i = 0; i < exceptionTypes.length; i++)
            eArgs.append(String.format(Locale.getDefault(), "%s %s", i == 0 ? "throws": ",",
                    exceptionTypes[i].getName().replace('$', '.')));

        return removeExtraSpaces(String.format("public %s %s %s(%s) %s { %s %s %s super.%s($$); %s %s %s }",
                modifiersList, returnName, methodSrc.getName(), pArgs, eArgs, isTry ? "try { ": "",
                action.equals(Action.INSERT_BEFORE) ? code: "", isVoid ? "": String.format(
                        "%s result =", returnName), methodSrc.getName(),
                action.equals(Action.INSERT_AFTER)  ? code: "", isVoid ? "": "return result;",
                !isTry ? "": action.equals(Action.INSERT_AFTER_FINALLY) ? String.format(
                        " } finally { %s }", code): String.format(" } catch(%s %s) { %s }",
                        getActionDescriptionRaw(data), EXCEPTION_NAME,
                        code.replace("$" + EXCEPTION_NAME, EXCEPTION_NAME))));
    }
}
