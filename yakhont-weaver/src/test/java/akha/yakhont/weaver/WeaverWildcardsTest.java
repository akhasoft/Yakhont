package akha.yakhont.weaver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import javassist.NotFoundException;

@SuppressWarnings("SpellCheckingInspection")
class WeaverWildcardsTest {

    private static final String TEST_AAR            = "./src/test/test.aar";
    private static final String TEST_REPLACE_MSG    = "jar class replace unit test";

    @Test
    void testWildCards() throws ClassNotFoundException, NoSuchMethodException,
            InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> cls = Class.forName("akha.yakhont.weaver.Weaver$WildCardsHandler");
        Object wildCardsHandler = cls.getConstructor(boolean.class).newInstance(true);

        splitTest          (wildCardsHandler, cls.getMethod("split"          , String.class              ));
        matchesTest        (wildCardsHandler, cls.getMethod("matches"        , String.class, String.class));
        matchesClassTest   (wildCardsHandler, cls.getMethod("matchesClass"   , String.class, String.class));
        getMethodStringTest(wildCardsHandler, cls.getMethod("getMethodString", String.class              ));
    }

    @Test
    void testArcHandler() throws CannotCompileException, ClassNotFoundException, NoSuchMethodException,
            InstantiationException, IllegalAccessException, InvocationTargetException, IOException,
            NotFoundException {
        Class<?> cls = Class.forName("akha.yakhont.weaver.Weaver$ArcHandler");
        Object arcHandler = cls.getConstructor(boolean.class).newInstance(true);

        getJarsTest(arcHandler, cls.getMethod("getJars", String.class, List  .class));
        replaceTest(arcHandler, cls.getMethod("replace", String.class, String.class, String.class));
    }

    @Test
    void testWeaver() throws NotFoundException, CannotCompileException {
        getNewMethodTest();
    }

    private void replaceTest(Object arcHandler, Method replace) throws CannotCompileException,
            IllegalAccessException, InvocationTargetException, IOException, NotFoundException {
        File jar = File.createTempFile("test", ".jar");

        ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(new FileInputStream(TEST_AAR)));
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null)
            if (zipEntry.getName().equals("classes.jar")) {
                FileOutputStream out = new FileOutputStream(jar);
                byte[] buffer = new byte[2048];
                int len;
                while ((len = zipInputStream.read(buffer)) != -1)
                    out.write(buffer, 0, len);
                out.close();
                break;
            }
        zipInputStream.close();

        JarFile jarFile = new JarFile(jar.getAbsolutePath());
        InputStream is = jarFile.getInputStream(jarFile.getEntry("akha/testaar/TestStub.class"));
        CtClass ctClass = new ClassPool(true).makeClass(is);
        is.close();
        jarFile.close();

        String jarPath = jar.getAbsolutePath().replace('\\', '/');
        File newClass = new File(jarPath.substring(0, jarPath.lastIndexOf('/')) + "/TestStub.class");
        ctClass.getMethod("test", "()V").setBody("System.out.println(\"" + TEST_REPLACE_MSG + "\");");

        FileOutputStream fileOutputStream = new FileOutputStream(newClass);
        ctClass.getClassFile().write(new DataOutputStream(fileOutputStream));
        fileOutputStream.close();

        replace.invoke(arcHandler, jar.getAbsolutePath(),
                "akha/testaar/TestStub.class", newClass.getAbsolutePath());

        Process process = Runtime.getRuntime().exec("java -cp " + jar.getAbsolutePath() +
                " akha.testaar.TestStub");

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = reader.readLine()) != null)
            stringBuilder.append(line).append("\n");

        try {
            if (process.waitFor() == 0)
                assertTrue(stringBuilder.toString().contains(TEST_REPLACE_MSG));
            else
                fail("failed to run akha.testaar.TestStub");
        }
        catch (InterruptedException exception) {
            throw new RuntimeException(exception);
        }
        finally {
            //noinspection ResultOfMethodCallIgnored
            newClass.delete();
            //noinspection ResultOfMethodCallIgnored
            jar.delete();
        }
    }

    private void getJarsTest(Object arcHandler, Method getJars) throws IllegalAccessException,
            InvocationTargetException, IOException {
        List<File> files = new ArrayList<>();

        getJars.invoke(arcHandler, TEST_AAR, files);
        assertEquals(3, files.size());

        List<String> entries = new ArrayList<>();
        for (File file: files) {
            ZipInputStream zipInputStream =
                    new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null)
                entries.add(zipEntry.getName());
            zipInputStream.close();

            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        assertTrue(entries.contains("akha/testaar/BuildConfig.class"));
        assertTrue(entries.contains("android/support/media/instantvideo/BuildConfig.class"));
        assertTrue(entries.contains("android/support/annotation/Nullable.class"));
    }

    private void getMethodStringTest(Object wildCardsHandler, Method getMethodString)
            throws IllegalAccessException, InvocationTargetException {
        assertEquals(getMethodString.invoke(wildCardsHandler,
                "public boolean java.lang.Object.equals(java.lang.Object)"),
                "boolean equals(java.lang.Object)");
        assertEquals(getMethodString.invoke(wildCardsHandler,
                "public static void someClass.someMethod() throws SomeException"),
                "void someMethod()");
        assertEquals(getMethodString.invoke(wildCardsHandler,
                "public static java.lang.Object someClass.someMethod() throws SomeException"),
                "java.lang.Object someMethod()");
        assertEquals(getMethodString.invoke(wildCardsHandler,
                "public static java.lang.Object someClass.someMethod(java.lang.Object) throws SomeException"),
                "java.lang.Object someMethod(java.lang.Object)");
    }

    @SuppressWarnings("unchecked")
    private void splitTest(Object wildCardsHandler, Method split)
            throws IllegalAccessException, InvocationTargetException {
        List<String> list = (List<String>) split.invoke(wildCardsHandler, "0.1.2");
        assertEquals(list.size(),  3);
        assertEquals(list.get(0), "0");
        assertEquals(list.get(1), "1");
        assertEquals(list.get(2), "2");

        list = (List<String>) split.invoke(wildCardsHandler, " 0 . 1 . 2 ");
        assertEquals(list.size(),  3);
        assertEquals(list.get(0), "0");
        assertEquals(list.get(1), "1");
        assertEquals(list.get(2), "2");

        list = (List<String>) split.invoke(wildCardsHandler, "0");
        assertEquals(list.size(),  1);
        assertEquals(list.get(0), "0");

        assertEquals(((List<String>) split.invoke(wildCardsHandler, "."  )).size(), 0);
        assertEquals(((List<String>) split.invoke(wildCardsHandler, " . ")).size(), 0);
        assertEquals(((List<String>) split.invoke(wildCardsHandler, ".0" )).size(), 0);
    }

    // based on https://www.rgagnon.com/javadetails/java-0515.html
    private void matchesTest(Object wildCardsHandler, Method matches)
            throws IllegalAccessException, InvocationTargetException {
        String test = "123ABC";

        assertTrue((Boolean) matches.invoke(wildCardsHandler, test, test));

        assertTrue ((Boolean) matches.invoke(wildCardsHandler, "1*"  , test));
        assertTrue ((Boolean) matches.invoke(wildCardsHandler, "?2*" , test));
        assertFalse((Boolean) matches.invoke(wildCardsHandler, "??2*", test));
        assertTrue ((Boolean) matches.invoke(wildCardsHandler, "*A*" , test));
        assertFalse((Boolean) matches.invoke(wildCardsHandler, "*Z*" , test));
        assertTrue ((Boolean) matches.invoke(wildCardsHandler, "123*", test));
        assertFalse((Boolean) matches.invoke(wildCardsHandler, "123" , test));
        assertTrue ((Boolean) matches.invoke(wildCardsHandler, "*ABC", test));
        assertFalse((Boolean) matches.invoke(wildCardsHandler, "*abc", test));
        assertFalse((Boolean) matches.invoke(wildCardsHandler, "ABC*", test));
    }

    private void matchesClassTest(Object wildCardsHandler, Method matchesClass)
            throws IllegalAccessException, InvocationTargetException {
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler, "A**B**"     , null));
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**."     , null));
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**C.D"   , null));

        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**"      , "A.B.C.D"));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**"      , "A.Bb.C.D"));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**.D"    , "A.Bb.C.D"));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler, "A.B**.C.D"  , "A.Bb.C.D"));

        String name = "io.reactivex.rxjava3.core.BackpressureOverflowStrategy";

        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rx*.c*.BackpressureOverflowStrategy"              , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactive*.rxjav?3.core.*"                                   , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rxjava3.core.BackpressureOverflowStrategy"        , name));

        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rxjava??.core.BackpressureOverflowStrategy"       , name));

        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.**"                                               , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactive**"                                                 , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reacti?e**"                                                 , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactive**.Backpressure*"                                   , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reacti?e**.Backpre?sure*"                                   , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reacti?e**.BackpressureOverflowStrategy"                    , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "i**.Backpre?sure*"                                             , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "**.Backpre?sure*"                                              , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "**.co?e.Backpre?sureOver*"                                     , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io**.rxjava?.core.BackpressureOverflowStrategy"                , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reacti?e**.co?e*.Backpre?sureOver*"                         , name));

        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rxjava??**"                                       , name));
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rxjava??**.Backpre?sure*"                         , name));
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reactivex.rxjava??**.BackpressureOverflowStrategy"          , name));
        assertFalse((Boolean) matchesClass.invoke(wildCardsHandler,
                "io.reacti??e**.co?e*.Backpre?sureOver*"                        , name));

        name = "akha.yakhont.LogDebug";

        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "akha.yakhont.LogDebug._D"                                      , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "akha.yakhont.LogDebug._R"                                      , name));
        assertTrue ((Boolean) matchesClass.invoke(wildCardsHandler,
                "akha.**.Lo?Debug."                                             , name));
    }

    private enum Action {       // should be consistent with Action in Weaver
        _before,
        _after,
        _finally,
        _catch
    }

    private String[] getData(Action action, String data) {
        return new String[] { String.valueOf(action.ordinal()), action.ordinal() == 3 ?
                "java.lang.Exception": action.name().substring(1), data, null };
    }

    private void newMethodTest(Weaver weaver, ClassPool classPool, CtClass classSrc, String method, String data)
            throws NotFoundException, CannotCompileException {
        String result = weaver.getNewMethod(classPool, CtNewMethod.make(method, classSrc),
                getData(Action._before, data), classSrc, true);
        methodTestCommon(classSrc,
                method.substring(0, method.indexOf('{') + 1) + " " + data + " }", result);
    }

    private void methodTestCommon(CtClass classSrc, String expected, String result) throws CannotCompileException {
        System.out.println("getNewMethod() result: " + result);
        CtNewMethod.make(result, classSrc);
        assertEquals(expected, result);
    }

    private void overriddenMethodTest(Weaver weaver, ClassPool classPool, CtClass classSrc,
                                      String methodName, String methodDescription, Action action,
                                      String data, String expected)
            throws NotFoundException, CannotCompileException {
        String result = weaver.getNewMethod(classPool, classSrc.getMethod(methodName, methodDescription),
                getData(action, data), null, false);
        methodTestCommon(classSrc, expected, result);
    }

    private void getNewMethodTest() throws NotFoundException, CannotCompileException {
        Weaver weaver = new Weaver();

        ClassPool classPool = new ClassPool(true);
        classPool.appendPathList("./build/classes/java/test/akha/yakhont/weaver/");

        CtClass classSrc = classPool.get("akha.yakhont.weaver.WeaverWildcardsTest$TestB");

        newMethodTest(weaver, classPool, classSrc,
                "public void x() {}", "int x = 1;");
        newMethodTest(weaver, classPool, classSrc,
                "protected final java.lang.String y() { return null; }", "return \"\";");
        newMethodTest(weaver, classPool, classSrc,
                "private static synchronized strictfp int z() { return 0; }", "return 1;");

        String data = "int y = 2;";

        overriddenMethodTest(weaver, classPool, classSrc, "test", "()V",
                Action._before, data, "protected void test() { " + data + " super.test($$); }");
        overriddenMethodTest(weaver, classPool, classSrc, "test", "()V",
                Action._after, data, "protected void test() { super.test($$); " + data + " }");

        overriddenMethodTest(weaver, classPool, classSrc, "testInt", "(II)I",
                Action._before, data, "protected int testInt(int __arg0, int __arg1) throws " +
                        "java.lang.Exception { " + data + " int __result = super.testInt($$); return __result; }");
        overriddenMethodTest(weaver, classPool, classSrc, "testInt", "(II)I",
                Action._after, data, "protected int testInt(int __arg0, int __arg1) throws " +
                        "java.lang.Exception { int __result = super.testInt($$); " + data + " return __result; }");

        overriddenMethodTest(weaver, classPool, classSrc, "test", "()V",
                Action._finally, data, "protected void test() { try { super.test($$); } finally { " +
                        data + " } }");
        overriddenMethodTest(weaver, classPool, classSrc, "testInt", "(II)I",
                Action._finally, data, "protected int testInt(int __arg0, int __arg1) throws " +
                        "java.lang.Exception { try { int __result = super.testInt($$); return __result; } " +
                        "finally { " + data + " } }");

        overriddenMethodTest(weaver, classPool, classSrc, "testVoidException", "()V",
                Action._catch, data, "public void testVoidException() throws java.lang.Exception " +
                        "{ try { super.testVoidException($$); } catch(java.lang.Exception __exception) { " +
                        data + " } }");
        overriddenMethodTest(weaver, classPool, classSrc, "testInt", "(II)I",
                Action._catch, "return $1;", "protected int testInt(int __arg0, int __arg1) throws " +
                        "java.lang.Exception { try { int __result = super.testInt($$); return __result; } " +
                        "catch(java.lang.Exception __exception) { return __arg0; } }");
        overriddenMethodTest(weaver, classPool, classSrc, "testInt", "(II)I",
                Action._catch, "throw $e;", "protected int testInt(int __arg0, int __arg1) throws " +
                        "java.lang.Exception { try { int __result = super.testInt($$); return __result; } " +
                        "catch(java.lang.Exception __exception) { throw __exception; } }");
    }

    private static class TestA {

        @SuppressWarnings({"EmptyMethod", "unused"})
        protected void test() {
        }

        @SuppressWarnings({"EmptyMethod", "RedundantThrows", "unused"})
        public void testVoidException() throws Exception {
        }

        @SuppressWarnings({"SameReturnValue", "RedundantThrows", "unused"})
        protected int testInt(int i, int j) throws Exception {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    private static class TestB extends TestA {
    }
}
