package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link VehiclesDbChunkKeyPatch} weaves the chunk-key rewrite into {@code
 * VehicleBuffer.set} only, {@link VehiclesDbChunkKeyRepairPatch} weaves the repair pass into {@code
 * SQLStore.initUsedIDs} only, and that {@link VehiclesDbChunkKeyFix#repair} re-files exactly the
 * rows whose {@code wx/wy} disagree with their {@code x,y}.
 */
class VehiclesDbChunkKeyPatchTest implements UnitTest {

    private static final String FIX = "io/pzstorm/storm/patch/fixes/VehiclesDbChunkKeyFix";
    private static final String VEHICLE_BUFFER = "zombie/vehicles/VehiclesDB2$VehicleBuffer";
    private static final String SQL_STORE = "zombie/vehicles/VehiclesDB2$SQLStore";

    @Test
    void setAdviceLandsOnlyInSet() throws Exception {
        byte[] raw = readClassBytes(VEHICLE_BUFFER + ".class");
        byte[] transformed = new VehiclesDbChunkKeyPatch().transform(raw);
        assertNotNull(transformed);

        assertEquals(0, countFixCalls(raw, "set", "chunkCoord"));
        assertEquals(2, countFixCalls(transformed, "set", "chunkCoord"));
        assertEquals(0, countFixCalls(transformed, "setBytes", "chunkCoord"));
    }

    @Test
    void repairAdviceLandsOnlyInInitUsedIDs() throws Exception {
        byte[] raw = readClassBytes(SQL_STORE + ".class");
        byte[] transformed = new VehiclesDbChunkKeyRepairPatch().transform(raw);
        assertNotNull(transformed);

        assertEquals(0, countFixCalls(raw, "initUsedIDs", "repair"));
        assertEquals(1, countFixCalls(transformed, "initUsedIDs", "repair"));
        assertEquals(0, countFixCalls(transformed, "create", "repair"));
    }

    @Test
    void chunkCoordFloorsNegatives() {
        assertEquals(565, VehiclesDbChunkKeyFix.chunkCoord(4520.107f));
        assertEquals(718, VehiclesDbChunkKeyFix.chunkCoord(5745.309f));
        assertEquals(0, VehiclesDbChunkKeyFix.chunkCoord(7.999f));
        assertEquals(-1, VehiclesDbChunkKeyFix.chunkCoord(-0.5f));
        assertEquals(-2, VehiclesDbChunkKeyFix.chunkCoord(-8.001f));
    }

    @Test
    void repairRefilesOnlyMismatchedRows() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement stat = conn.createStatement()) {
                stat.executeUpdate(
                        "CREATE TABLE vehicles (id INTEGER PRIMARY KEY NOT NULL, wx INTEGER,"
                                + " wy INTEGER, x FLOAT, y FLOAT, inMeta BOOLEAN NULL DEFAULT"
                                + " FALSE, worldversion INTEGER, data BLOB)");
                stat.executeUpdate(
                        "INSERT INTO vehicles VALUES (1, 565, 718, 4520.1, 5745.3, 0, 1, NULL)");
                stat.executeUpdate(
                        "INSERT INTO vehicles VALUES (620, 354, 731, 4520.1, 5745.3, 0, 1, NULL)");
                stat.executeUpdate(
                        "INSERT INTO vehicles VALUES (227, 0, 0, 2524.4, 8299.6, 0, 1, NULL)");
                stat.executeUpdate("INSERT INTO vehicles VALUES (9, 5, 5, NULL, NULL, 0, 1, NULL)");
            }
            conn.setAutoCommit(false);

            VehiclesDbChunkKeyFix.repair(conn);

            assertEquals("565,718", keyOf(conn, 1));
            assertEquals("565,718", keyOf(conn, 620));
            assertEquals("315,1037", keyOf(conn, 227));
            assertEquals("5,5", keyOf(conn, 9));
            conn.rollback();
            assertEquals("565,718", keyOf(conn, 620), "repair must commit its rewrites");
        }
    }

    private static String keyOf(Connection conn, int id) throws Exception {
        try (Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT wx, wy FROM vehicles WHERE id = " + id)) {
            assertTrue(rs.next());
            return rs.getInt(1) + "," + rs.getInt(2);
        }
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countFixCalls(byte[] classBytes, String method, String fixMethod) {
        int[] hits = new int[1];
        new ClassReader(classBytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions) {
                                if (!method.equals(name)) {
                                    return null;
                                }
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String mName,
                                            String mDesc,
                                            boolean isInterface) {
                                        if (FIX.equals(owner) && fixMethod.equals(mName)) {
                                            hits[0]++;
                                        }
                                    }
                                };
                            }
                        },
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return hits[0];
    }
}
