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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * The <code>Weaver</code> class weaves the given application's compiled classes according to the configuration file info.
 *
 * @author akha
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class Weaver {

    private static final String COMMENT             = "#" ;
    private static final String DELIMITER           = " " ;
    private static final String EXCEPTION_NAME      = "e" ;
    private static final String IGNORE_SIGNATURE    = "()";

    private static final String DEF_CONFIG          = "weaver.config";

    private        final Map<String, Map<String, List<String>>>
                                mMethodsToWeave     = new LinkedHashMap<>();

    private        final String mNewLine;

    private enum Actions {
        INSERT_BEFORE,
        INSERT_AFTER,
        INSERT_AFTER_FINALLY,
        CATCH
    }

    private String mApplicationId, mClassPath, mBootClassPath;
    private boolean mDebug;

    /**
     * Initialises a newly created {@code Weaver} object.
     */
    public Weaver() {
        mNewLine = System.getProperty("line.separator");
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
     * @param applicationId
     *        The Android Application ID (normally {@code android.defaultConfig.applicationId} from application's {@code build.gradle})
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
     * @param configFiles
     *        The names (and locations) of configuration files (if any), or null (means default one)
     *
     * @param addConfig
     *        Indicates whether the configuration file provided should be added to the default one
     *
     * @throws  NotFoundException
     *          please refer to the exception description
     * @throws  CannotCompileException
     *          please refer to the exception description
     * @throws  IOException
     *          please refer to the exception description
     */
    public void run(boolean debug, String applicationId, String classesDir, String classPath,
                    String bootClassPath, String[] configFiles, boolean addConfig)
            throws NotFoundException, CannotCompileException, IOException {

        log(false, "Yakhont: weaving compiled classes in " + classesDir);
        mDebug = debug;

        mApplicationId = applicationId;
        mClassPath     = classPath;
        mBootClassPath = bootClassPath;

        mMethodsToWeave.clear();

        log(mNewLine + "classpath:");
        for (String token : classPath.split(File.pathSeparator)) log(token);
        log(mNewLine + "boot classpath:");
        for (String token : bootClassPath.split(File.pathSeparator)) log(token);

        log(mNewLine + "compiled classes dir: " + classesDir + mNewLine + mNewLine +
                "application id: " + applicationId);

        if (addConfig) parseConfig();
        if (configFiles != null) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < configFiles.length; i++)
                parseConfig(configFiles[i]);
        }
        printConfig(mMethodsToWeave);

        searchClasses(new File(classesDir).listFiles());
    }

    private void parseConfig() throws IOException, CannotCompileException {
        log(mNewLine + mNewLine + "config file: default");
        String line;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(DEF_CONFIG)))) {
            while ((line = in.readLine()) != null)
                parseConfig(line, mMethodsToWeave);
        }
    }

    private void parseConfig(String configFile) throws IOException, CannotCompileException {
        if (configFile == null) return;
        configFile = configFile.replace("\\", File.separator)
                .replace("/", File.separator);
        log(mNewLine + mNewLine + "config file: " + configFile);

        for (String line: Files.readAllLines(Paths.get(configFile), Charset.defaultCharset()))
            parseConfig(line, mMethodsToWeave);
    }

    /**
     * Parses the configuration file.
     *
     * @param line
     *        The line to parse
     *
     * @param map
     *        The internal representation of the configuration info (after successful parsing)
     *
     * @throws  CannotCompileException
     *          please refer to the exception description
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    protected void parseConfig(String line, Map<String, Map<String, List<String>>> map) throws CannotCompileException {
        int idx = line.indexOf(COMMENT);
        if (idx >= 0) line = line.substring(0, idx);

        List<String> tokens = new LinkedList<>();
        for (Matcher m = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'").matcher(line.trim()); m.find();)
            tokens.add(m.group().replace("\"", "")
                    .replace("'", ""));

        if (tokens.size() == 0) return;
        if (tokens.size() < 3)
            throw new CannotCompileException("error - invalid line: " + line);

        Actions action;
        String actionToken = tokens.get(1);
        switch (actionToken.toLowerCase()) {
            case "before":
                action = Actions.INSERT_BEFORE;
                break;
            case "after":
                action = Actions.INSERT_AFTER;
                break;
            case "finally":
                action = Actions.INSERT_AFTER_FINALLY;
                break;
            default:
                action = Actions.CATCH;
                break;
        }

        String methodName = tokens.get(0).substring(tokens.get(0).lastIndexOf(".") + 1);
        String  className = tokens.get(0).substring(0, tokens.get(0).length() - methodName.length() - 1);

        if (!map.containsKey(className))        //noinspection Convert2Diamond
            map.put(className, new LinkedHashMap<String, List<String>>());
        Map<String, List<String>> classMap = map.get(className);

        if (!classMap.containsKey(methodName))  //noinspection Convert2Diamond
            classMap.put(methodName, new ArrayList<String>());
        classMap.get(methodName).add(
                action.ordinal() + actionToken + DELIMITER + removeExtraSpaces(tokens.get(2)));
    }

    private void printConfig(Map<String, Map<String, List<String>>> map) {
        log(mNewLine + "START OF CONFIG");
        for (String key: map.keySet()) {
            log(mNewLine + "--- " + key);
            for (String method: map.get(key).keySet()) {
                log(mNewLine + "   " + method);
                for (String methodData: map.get(key).get(method))
                    log("    action: " + getActionDescription(methodData) + ", code: '" +
                            getCode(methodData) + "'");
            }
        }
        log(mNewLine + "END OF CONFIG");
    }

    private String removeExtraSpaces(String str) {
        return str.replaceAll("\\s+", " ");
    }

    private Actions getAction(String methodData) {
        return Actions.values()[Integer.valueOf(methodData.substring(0, 1))];
    }

    private String getActionDescription(String methodData) {
        Actions action = getAction(methodData);
        return (action.equals(Actions.CATCH) ? "catch ": "") + getActionDescriptionRaw(methodData);
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
                insertMethods(file);
    }

    private void insertMethods(File destClassFile) throws NotFoundException, CannotCompileException, IOException {
        String destClassName, path = destClassFile.getCanonicalPath();

        int idx = path.indexOf(mApplicationId.replace(".", File.separator));
        if (idx < 0) return;

        destClassName  = path.substring(idx).replace(File.separator, ".")
                .substring(0, path.length() - idx - 6);     // remove .class
        String rootDir = path.substring(0, idx);

        // getDefault() returns singleton but we need clear instance
        ClassPool pool = new ClassPool(true); // ClassPool.getDefault();
        pool.insertClassPath(rootDir.endsWith(File.separator) ?
                rootDir.substring(0, rootDir.length() - 1): rootDir);

        pool.appendPathList(mClassPath);
        pool.appendPathList(mBootClassPath);

        CtClass clsDest = pool.get(destClassName);
        if (!clsDest.getPackageName().startsWith(mApplicationId)) return;

        for (String className: mMethodsToWeave.keySet()) {
            CtClass clsSrc = pool.getOrNull(className);
            if (clsSrc == null) throw new CannotCompileException("error - can not find class: " + className);

            if (clsDest.subclassOf(clsSrc)) {
                log(mNewLine + "--- class to weaving: " + destClassName + " (based on " + clsSrc.getName() + ")");
                for (String methodName: mMethodsToWeave.get(className).keySet())
                    insertMethods(clsDest, clsSrc, methodName, pool);
            }
        }
        if (!clsDest.isModified()) return;

        log(mNewLine + "about to write class " + clsDest.getName() + " to " + rootDir);
        clsDest.writeFile(rootDir.endsWith(File.separator) ? rootDir: rootDir + File.separator);
    }

    private void insertMethods(CtClass clsDest, CtClass clsSrc, String methodName, ClassPool pool)
            throws NotFoundException, CannotCompileException {
        List<String> methodData = mMethodsToWeave.get(clsSrc.getName()).get(methodName);
        for (int i = methodData.size() - 1; i >= 0; i--) {
            if (Collections.frequency(methodData, methodData.get(i)) > 1)
                log(false, "warning - duplicated entries for method " + methodName +
                        ": " + methodData.get(i));

            int idx = methodName.indexOf("(");
            CtMethod[] methods = clsSrc.getDeclaredMethods(idx < 0 ? methodName:
                    methodName.substring(0, idx));

            boolean ignoreSignature = methodName.endsWith(IGNORE_SIGNATURE);
            if (ignoreSignature) idx = -1;
            if (methods.length > 1 && idx < 0 && !ignoreSignature)
                log(false, "warning - there're several methods " + methodName +
                        ", please specify signature or use " + IGNORE_SIGNATURE);

            for (CtMethod methodSrc: methods) {
                if (idx >= 0 && !methodSrc.getSignature().startsWith(methodName.substring(idx))) continue;

                CtMethod methodDest = null;
                try {
                    methodDest = clsDest.getDeclaredMethod(methodSrc.getName(), methodSrc.getParameterTypes());
                }
                catch (NotFoundException ignored) {
                }

                if (methodDest != null) {
                    log(mNewLine + "method " + methodDest.getLongName() +
                            " is already overridden; about to weave, action: "   +
                            getActionDescription(methodData.get(i)) + ", code: " +
                            getCode(methodData.get(i)));

                    Actions action = getAction(methodData.get(i));
                    switch (action) {
                        case INSERT_BEFORE:
                            methodDest.insertBefore(getCode(methodData.get(i)));
                            break;
                        case INSERT_AFTER:
                            methodDest.insertAfter(getCode(methodData.get(i)));
                            break;
                        case INSERT_AFTER_FINALLY:
                            methodDest.insertAfter(getCode(methodData.get(i)), true);
                            break;
                        case CATCH:
                            methodDest.addCatch(getCode(methodData.get(i)),
                                    pool.get(getActionDescriptionRaw(methodData.get(i))),
                                    "$" + EXCEPTION_NAME);
                            break;
                        default:        // should never happen
                            throw new CannotCompileException("error - unknown action: " + action);
                    }
                }
                else {
                    String newMethod = newMethod(methodSrc, methodData.get(i), clsDest);

                    log(mNewLine + "about to add method " + methodName + mNewLine +
                            " method body: " + newMethod);

                    clsDest.addMethod(CtNewMethod.make(newMethod, clsDest));
                }
            }
        }
    }

    /**
     * Creates a new overriding method in the destination class.
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
    protected String newMethod(CtMethod methodSrc, String data, CtClass clsDest)
            throws NotFoundException, CannotCompileException {
        // it seems CtNewMethod.delegator(...) is not applicable here

        int modifiers = methodSrc.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers))
            throw new CannotCompileException("error - can not override " + methodSrc.getName() +
                    " 'cause it's static (or final or private)");

        CtClass[] paramTypes = methodSrc.getParameterTypes(), exceptionTypes = methodSrc.getExceptionTypes();
        if (paramTypes == null) paramTypes = new CtClass[] {};

        CtClass returnType = methodSrc.getReturnType();
        boolean isVoid = returnType.equals(CtClass.voidType);
        String returnName = returnType.getName().replace('$', '.');

        Actions action = getAction(data);
        boolean isTry  = action.equals(Actions.INSERT_AFTER_FINALLY) || action.equals(Actions.CATCH);

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

        return removeExtraSpaces(String.format("public %s %s(%s) %s { %s %s %s super.%s($$); %s %s %s }",
                returnName, methodSrc.getName(), pArgs, eArgs, isTry ? "try { ": "",
                action.equals(Actions.INSERT_BEFORE) ? code: "", isVoid ? "": String.format(
                        "%s result =", returnName), methodSrc.getName(),
                action.equals(Actions.INSERT_AFTER)  ? code: "", isVoid ? "": "return result;",
                !isTry ? "": action.equals(Actions.INSERT_AFTER_FINALLY) ? String.format(
                        " } finally { %s }", code): String.format(" } catch(%s %s) { %s }",
                        getActionDescriptionRaw(data), EXCEPTION_NAME,
                        code.replace("$" + EXCEPTION_NAME, EXCEPTION_NAME))));
    }
}
