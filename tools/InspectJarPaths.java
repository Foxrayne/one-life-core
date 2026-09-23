import java.lang.classfile.ClassFile;
import java.util.zip.ZipFile;

class InspectJarPaths {
    public static void main(String[] args) throws Exception {
        try (ZipFile jar = new ZipFile(args[0])) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                var model = ClassFile.of().parse(jar.getInputStream(entry).readAllBytes());
                String expected = model.thisClass().asInternalName() + ".class";
                if (!entry.getName().equals(expected)) {
                    System.out.println(entry.getName() + " -> " + expected);
                }
            }
        }
    }
}
