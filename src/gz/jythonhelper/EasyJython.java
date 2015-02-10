package gz.jythonhelper;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: gongze
 * Date: 1/30/13
 * Time: 2:52 PM
 */
public class EasyJython {

    public static final Pattern PATTERN_FROM_IMPORT = Pattern.compile("\\s*from\\s*(\\S+)\\s*import\\s+(\\w[\\w,\\p{Blank}]*)");

    public static final Pattern PATTERN_IMPORT = Pattern.compile("\\s*import\\s*(\\S+)");

    public static final Pattern PATTERN_FROM_IMPORT_MULTIPLE = Pattern.compile("\\s*from\\s*(\\S+)\\s*import\\s+\\(\\s*(.*)\\s*\\)", Pattern.MULTILINE);

    public static final String[] PREDEFINED_CLASSES = {};

    ClassGenerator cg;
    private ClassLoader classLoader;

    public EasyJython(String outputDir) {
        cg = new PyClassGenerator(outputDir);
    }

    public void setLibs(String[] libs) {
        List<URL> urls = new ArrayList<URL>();
        for (String name : libs) {
            try {
                if (name.endsWith("*")) {
                    name = name.substring(0, name.length() - 1);
                }
                File file = new File(name);
                if (file.isDirectory()) {
                    File[] files = file.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".jar");
                        }
                    });
                    for (File f : files) {
                        urls.add(f.toURI().toURL());
                    }
                } else {
                    urls.add(file.toURI().toURL());
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    private static String[] getFilesFromArgs(String[] args) {
        List<String> list = new ArrayList<String>();
        for (String arg : args) {
            String[] split = arg.split(";|:");
            list.addAll(Arrays.asList(split));
        }
        return list.toArray(new String[list.size()]);
    }

    public void generatePys(String... pyFiles) {
        Set<String> classList = new HashSet<String>();
        classList.addAll(Arrays.asList(PREDEFINED_CLASSES));
        for (String f : pyFiles) {
            File file = new File(f);
            if (file.isFile()) {
                System.out.println("File:--->" + file.getName());
                classList.addAll(getClassListFromPy(file));
            } else {
                System.out.println("Directory:--->" + file.getAbsolutePath());
                File[] files = file.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".py");
                    }
                });
                if (files != null) {
                    for (File child : files) {
                        System.out.println("--->" + child.getName());
                        classList.addAll(getClassListFromPy(child));
                    }
                }
            }
        }

        for (String className : classList) {
            try {
                System.out.println("Generating:" + className);
                Class<?> clazz = Class.forName(className, false, classLoader);
                System.out.println(clazz);
                cg.createPyForClass(clazz);
            } catch (ClassNotFoundException e) {
                System.err.println("Class not found:" + e.getMessage());
            } catch (IOException e) {
                System.err.println("IO Exception:" + e.getMessage());
            }
        }
    }

    private Set<String> getClassListFromPy(File file) {
        StringBuilder sb = new StringBuilder();
        Set<String> classes = new HashSet<String>();
        try {
            String line;
            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
                Matcher matcher = PATTERN_FROM_IMPORT.matcher(line);
                if (matcher.matches()) {
                    System.out.println(line);
                    String packageName = matcher.group(1);
                    String classSimpleName = matcher.group(2);
                    String[] names = classSimpleName.split(",");//for from xyz import Abc, Def, Ghi
                    for (String className : names) {
                        className = className.trim();
                        if (!className.isEmpty()) {
                            String[] split = className.split(" ");// for import Abc as abc
                            className = split[0].trim();
                            classes.add(packageName + "." + className);
                        }
                    }
                    continue;
                }

                matcher = PATTERN_IMPORT.matcher(line);
                if (matcher.matches()) {
                    String classSimpleName = matcher.group(1);
                    classes.add(classSimpleName);
                }
            }
            br.close();
            Matcher matcher = PATTERN_FROM_IMPORT_MULTIPLE.matcher(sb.toString());
            while (matcher.find()) {
                String packageName = matcher.group(1);
                String classSimpleName = matcher.group(2);
                String[] names = classSimpleName.split(",");//for from xyz import Abc, Def, Ghi
                for (String className : names) {
                    className = className.trim();
                    if (!className.isEmpty()) {
                        String[] split = className.split(" ");// for import Abc as abc
                        className = split[0].trim();
                        classes.add(packageName + "." + className);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }
}

abstract class ClassGenerator {
    public static final String INIT_PY = "__init__.py";
    public static final String DEF_TPL = "def %s(%s):";
    public static final String CLASS_TPL = "class %s:";
    public static final String PASS = "pass";
    private static final Object INDENT = "    ";
    public static final String STATIC_METHOD = "@staticmethod";

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final Set<String> IGNORED_FIELDS = new HashSet<String>(Arrays.asList(new String[]{
            "in"
    }));
    public static final Set<String> IGNORED_METHODS = new HashSet<String>(Arrays.asList(new String[]
            {"wait", "toString", "hashCode", "notify", "notifyAll", "getClass", "yield"}));

    private String outputDir;
    public static final String INIT_TEMPLATE = "# encoding: utf-8\n" +
            "# module %s\n" +
            "# from (built-in)\n" +
            "# by generator 999.999\n" +
            "# source:%s\n";

    protected ClassGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    String indents(String line, int i) {
        String[] split = line.split(LINE_SEPARATOR);
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            sb.append(indent(s, i));
        }
        return sb.toString();
    }


    String indent(String line) {
        return indent(line, 1);
    }

    String indent(String line, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(INDENT);
        }
        sb.append(line);
        return sb.toString();
    }

    String makeAField(Field f) {
        return f.getName() + " = None";
    }

    String indents(String line) {
        String[] split = line.split(LINE_SEPARATOR);
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            sb.append(indent(s, 1));
            sb.append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    void writePyHeader(FileWriter fw, String name, URL resourceURL) {
        String source = "";
        if (resourceURL != null) {
            source = resourceURL.toString();
        }
        try {
            fw.write(String.format(INIT_TEMPLATE, name, source));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void ensureInitPy(File dir) {
        File initFile = new File(dir, INIT_PY);
//        if (initFile.exists() && initFile.lastModified() < startTime) {
//            initFile.delete();
//        }
        if (!initFile.exists()) {
            try {
                boolean newFile = initFile.createNewFile();
                if (newFile) {
                    FileWriter fw = new FileWriter(initFile);
                    writePyHeader(fw, initFile.getName(), null);
                    fw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    File createDirectory(Class clazz) {
        String packageName = clazz.getPackage().getName();
        System.out.println("create package directory:" + packageName);

        String[] split = packageName.split("\\.");
        File currentDir = new File(outputDir);
        for (String dirName : split) {
            File tmpDir = new File(currentDir, dirName);
            if (!tmpDir.exists()) {
                tmpDir.mkdir();
            }
            ensureInitPy(tmpDir);
            currentDir = tmpDir;
        }
        return currentDir;
    }

    Method[] filterOverrideMethods(Method[] methods) {
        Map<String, Method> methodMap = new HashMap<String, Method>();

        for (Method m : methods) {
            String name = m.getName();
            Method existingMethod = methodMap.get(name);
            if (existingMethod == null) {
                methodMap.put(name, m);
            } else {
                Class<?>[] parameterTypes = existingMethod.getParameterTypes();
                Class<?>[] newParameterTypes = m.getParameterTypes();
                if (newParameterTypes.length > parameterTypes.length) {
                    methodMap.put(name, m);
                }
            }
        }
        return methodMap.values().toArray(new Method[methodMap.keySet().size()]);
    }

    public abstract void createPyForClass(Class<?> clazz) throws IOException;
}

class PyClassGenerator extends ClassGenerator {

    Pattern CLASS_NAME_PATTERN = Pattern.compile("class\\s+(\\w+).*:");

    protected PyClassGenerator(String outputDir) {
        super(outputDir);
    }

    public void createPyForClass(Class clazz) throws IOException {
        File directory = createDirectory(clazz);
        String classContent = generateClassAsPyClass(clazz);
        File initPy = new File(directory, INIT_PY);
        Map<String, StringBuilder> classStringMap = readClassesFromInitPy(initPy);
        classStringMap.put(clazz.getSimpleName(), new StringBuilder(classContent));
        String name = clazz.getName().replace('.', '/') + ".class";
        ClassLoader classLoader = clazz.getClassLoader();
        URL resourceURL = null;
        if (classLoader != null) {
            resourceURL = classLoader.getResource(name);
        }
        writeClassesFromInitPy(classStringMap, initPy, resourceURL);
    }

    public void createPyForClass0(Class clazz) throws IOException {
        File directory = createDirectory(clazz);
        String classContent = generateClassAsPyClass(clazz);
        File classFile = new File(directory, INIT_PY);
        FileWriter fileWriter = new FileWriter(classFile, true);
        fileWriter.write(classContent);
        fileWriter.close();
    }

    private Map<String, StringBuilder> readClassesFromInitPy(File initFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(initFile));
        String line;
        boolean inClass = false;
        Map<String, StringBuilder> maps = new HashMap<String, StringBuilder>();
        StringBuilder sb = null;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("class")) {
                inClass = true;
                Matcher matcher = CLASS_NAME_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String className = matcher.group(1);
                    sb = new StringBuilder();
                    maps.put(className, sb);
                }
            }
            if (inClass && sb != null) {
                sb.append(line).append(LINE_SEPARATOR);
            }
        }
        br.close();
        return maps;
    }

    private void writeClassesFromInitPy(Map<String, StringBuilder> classStringMap, File initPy, URL resourceURL) throws IOException {
        FileWriter fileWriter = new FileWriter(initPy);
        writePyHeader(fileWriter, initPy.getName(), resourceURL);
        for (StringBuilder classContent : classStringMap.values()) {
            fileWriter.write(classContent.toString());
        }
        fileWriter.close();
    }

    private String generateClassAsPyClass(Class clazz) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(CLASS_TPL, clazz.getSimpleName()));
        sb.append(LINE_SEPARATOR);
        Field[] fields = new Field[0];
        try {
            fields = clazz.getFields();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers)) {
                if (!IGNORED_FIELDS.contains(f.getName())) {
                    sb.append(indent(makeAField(f)));
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        sb.append(LINE_SEPARATOR);
        Method[] methods = new Method[0];
        try {
            methods = clazz.getMethods();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        methods = filterOverrideMethods(methods);
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            if (Modifier.isPublic(modifiers)) {
                String name = m.getName();
                if (!IGNORED_METHODS.contains(name)) {
                    sb.append(indents(generateMethodForPyClass(m)));
                    sb.append(indents(PASS, 2));
                    sb.append(LINE_SEPARATOR);
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        return sb.toString();
    }

    private String generateMethodForPyClass(Method m) {
        Class<?>[] parameterTypes = m.getParameterTypes();
        StringBuilder sb = new StringBuilder();
        int modifiers = m.getModifiers();
        String prefix = "";
        if (Modifier.isStatic(modifiers)) {
            prefix = STATIC_METHOD + LINE_SEPARATOR;
        } else {
            sb.append("self, ");
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            String typeName;
            if (type.isArray()) {
                typeName = type.getComponentType().getSimpleName();
            } else {
                typeName = type.getSimpleName();
            }
            sb.append(typeName + (i > 0 ? i : "") + "=None");
            if (i < parameterTypes.length - 1) {
                sb.append(", ");
            }
        }

        return prefix + String.format(DEF_TPL, m.getName(), sb.toString());
    }
}

class PyModuleGenerator extends ClassGenerator {
    protected PyModuleGenerator(String outputDir) {
        super(outputDir);
    }

    public void createPyForClass(Class clazz) throws IOException {
        File directory = createDirectory(clazz);
        String classContent = generateClassAsModule(clazz);
        if (classContent == null) {
            return;
        }
        File classFile = new File(directory, clazz.getSimpleName() + ".py");
        FileWriter fileWriter = new FileWriter(classFile);
        fileWriter.write(classContent);
        fileWriter.close();
    }

    private String generateMethodForModule(Method m) {
        Class<?>[] parameterTypes = m.getParameterTypes();
        StringBuilder sb = new StringBuilder();
        int modifiers = m.getModifiers();
        String prefix = "";
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            String typeName;
            if (type.isArray()) {
                typeName = type.getComponentType().getSimpleName();
            } else {
                typeName = type.getSimpleName();
            }
            sb.append(typeName + (i > 0 ? i : "") + "=None");
            if (i < parameterTypes.length - 1) {
                sb.append(", ");
            }
        }

        return prefix + String.format(DEF_TPL, m.getName(), sb.toString());
    }


    private String generateClassAsModule(Class clazz) {
        StringBuilder sb = new StringBuilder();
        Field[] fields = new Field[0];

        try {
            fields = clazz.getFields();
        } catch (Throwable e) {
            System.err.println("Get Field error:" + e.getMessage());
        }
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers)) {
                if (!IGNORED_FIELDS.contains(f.getName())) {
                    sb.append(indent(makeAField(f), 0));
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        sb.append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);
        Method[] methods = new Method[0];
        try {
            methods = clazz.getMethods();
        } catch (Throwable e) {
            System.err.println("Get Method error:" + e.getMessage());
        }
        methods = filterOverrideMethods(methods);
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            if (Modifier.isPublic(modifiers)) {
                String name = m.getName();
                if (!IGNORED_METHODS.contains(name)) {
                    sb.append(indents(generateMethodForModule(m), 0));
                    sb.append(LINE_SEPARATOR);
                    sb.append(indents(PASS, 2));
                    sb.append(LINE_SEPARATOR);
                    sb.append(LINE_SEPARATOR);
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        return sb.toString();
    }
}