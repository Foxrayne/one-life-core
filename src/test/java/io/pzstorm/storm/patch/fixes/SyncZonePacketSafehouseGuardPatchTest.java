package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import zombie.core.network.ByteBufferReader;
import zombie.iso.areas.DesignationZone;
import zombie.iso.areas.DesignationZoneAnimal;
import zombie.iso.areas.SafeHouse;

/**
 * Verifies that {@link SyncZonePacketSafehouseGuardPatch} weaves the guard into {@code
 * SyncZonePacket.parse} only, and unit-tests the safehouse rule, the group split, and the header
 * peek in {@link AnimalZoneSafehouseGuard}.
 *
 * <p>Zones are {@code Unsafe.allocateInstance}d because the real constructor calls {@code check()},
 * which needs a live {@code IsoWorld}.
 */
class SyncZonePacketSafehouseGuardPatchTest implements UnitTest {

    private static final String TARGET_CLASS = "zombie/network/packets/SyncZonePacket";
    private static final String HELPER_CLASS =
            "io/pzstorm/storm/patch/fixes/AnimalZoneSafehouseGuard";

    private static final List<String> ALICE = List.of("alice");
    private static final List<String> BOB = List.of("bob");

    private static Unsafe unsafe;

    @BeforeAll
    static void setUpUnsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (Unsafe) f.get(null);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        DesignationZoneAnimal.designationAnimalZoneList.clear();
        DesignationZone.allZones.clear();
        SafeHouse.getSafehouseList().clear();
        AnimalZoneSafehouseGuard.setEnabled(AnimalZoneSafehouseGuard.DEFAULT_ENABLED);
        AnimalZoneSafehouseGuard.resetBroken();
    }

    private static DesignationZoneAnimal zone(double id, int x, int y, int w, int h)
            throws Exception {
        DesignationZoneAnimal zone =
                (DesignationZoneAnimal) unsafe.allocateInstance(DesignationZoneAnimal.class);
        zone.id = id;
        zone.name = "zone" + (int) id;
        zone.type = "AnimalZone";
        zone.x = x;
        zone.y = y;
        zone.w = w;
        zone.h = h;
        DesignationZoneAnimal.designationAnimalZoneList.add(zone);
        DesignationZone.allZones.add(zone);
        return zone;
    }

    private static SafeHouse safehouse(int x, int y, int w, int h, String owner) {
        SafeHouse safe = new SafeHouse(x, y, w, h, owner);
        SafeHouse.getSafehouseList().add(safe);
        return safe;
    }

    @Test
    void patchInjectsGuardIntoParseOnly() throws Exception {
        byte[] rawClass = readClassBytes(TARGET_CLASS + ".class");
        byte[] transformed = new SyncZonePacketSafehouseGuardPatch().transform(rawClass);
        assertNotNull(transformed);

        assertEquals(0, countHelperCalls(rawClass, "parse"));
        assertEquals(2, countHelperCalls(transformed, "parse"));
        assertEquals(0, countHelperCalls(transformed, "processServer"));
        assertEquals(0, countHelperCalls(transformed, "processClient"));
        assertEquals(0, countHelperCalls(transformed, "write"));
    }

    @Test
    void zoneOutsideEverySafehouseIsEditableByAnyone() {
        safehouse(100, 100, 10, 10, "alice");
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 0, 0, 20, 20));
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 110, 100, 5, 5));
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 95, 100, 5, 5));
    }

    @Test
    void zoneOverlappingSafehouseIsEditableByOwnerAndMembersOnly() {
        SafeHouse safe = safehouse(100, 100, 10, 10, "alice");
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(ALICE, false, 105, 105, 20, 20));
        assertFalse(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 105, 105, 20, 20));
        assertFalse(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 109, 109, 1, 1));
        assertFalse(AnimalZoneSafehouseGuard.mayEdit(List.of(), false, 105, 105, 2, 2));

        safe.getPlayers().add("bob");
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 105, 105, 20, 20));
    }

    @Test
    void bypassRoleEditsAnything() {
        safehouse(100, 100, 10, 10, "alice");
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, true, 105, 105, 2, 2));
    }

    @Test
    void zoneOverlappingTwoSafehousesIsEditableByEitherSide() {
        safehouse(100, 100, 10, 10, "alice");
        safehouse(115, 100, 10, 10, "bob");
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(ALICE, false, 105, 100, 15, 5));
        assertTrue(AnimalZoneSafehouseGuard.mayEdit(BOB, false, 105, 100, 15, 5));
        assertFalse(AnimalZoneSafehouseGuard.mayEdit(List.of("carol"), false, 105, 100, 15, 5));
    }

    @Test
    void groupSplitKeepsOnlyZonesTheRequesterMayNotEdit() throws Exception {
        safehouse(100, 100, 10, 10, "alice");
        DesignationZoneAnimal alicePen = zone(1, 102, 102, 4, 4);
        DesignationZoneAnimal bobPen = zone(2, 106, 102, 10, 4);
        DesignationZoneAnimal wildPen = zone(3, 116, 102, 4, 4);
        ArrayList<DesignationZoneAnimal> group =
                DesignationZoneAnimal.getAllDZones(null, wildPen, null);
        assertEquals(3, group.size());

        ArrayList<DesignationZoneAnimal> keptFromBob =
                AnimalZoneSafehouseGuard.protectedFrom(group, BOB, false);
        assertEquals(2, keptFromBob.size());
        assertTrue(keptFromBob.contains(alicePen));
        assertTrue(keptFromBob.contains(bobPen));

        assertTrue(AnimalZoneSafehouseGuard.protectedFrom(group, ALICE, false).isEmpty());
        assertTrue(AnimalZoneSafehouseGuard.protectedFrom(group, BOB, true).isEmpty());
    }

    @Test
    void disabledGuardLeavesThePacketAlone() {
        assertNull(AnimalZoneSafehouseGuard.beforeParse(reader(true, false, 1.0)));
    }

    @Test
    void peekSnapshotsTheExistingZoneWithoutConsumingTheBuffer() throws Exception {
        AnimalZoneSafehouseGuard.setEnabled(true);
        DesignationZoneAnimal existing = zone(42, 10, 10, 5, 6);

        ByteBufferReader reader = reader(true, true, 42.0);
        int position = reader.bb.position();
        AnimalZoneSafehouseGuard.Pending pending =
                (AnimalZoneSafehouseGuard.Pending) AnimalZoneSafehouseGuard.beforeParse(reader);
        assertEquals(position, reader.bb.position());
        assertTrue(pending.designation);
        assertTrue(pending.added);
        assertSame(existing, pending.existing);
        assertEquals("zone42", pending.name);
        assertEquals(5, pending.w);
        assertEquals(6, pending.h);

        AnimalZoneSafehouseGuard.Pending unknown =
                (AnimalZoneSafehouseGuard.Pending)
                        AnimalZoneSafehouseGuard.beforeParse(reader(true, true, 7.0));
        assertNull(unknown.existing);
    }

    @Test
    void nonDesignationPacketIsVetoedSoAStaleZoneIsNeverReplayed() {
        AnimalZoneSafehouseGuard.setEnabled(true);
        Object pending = AnimalZoneSafehouseGuard.beforeParse(reader(false, false, 0.0));
        assertNotNull(pending);
        assertTrue(AnimalZoneSafehouseGuard.afterParse(pending, null, null));
        assertFalse(AnimalZoneSafehouseGuard.isBroken());
    }

    @Test
    void badConnectionLatchesOffInsteadOfThrowing() throws Exception {
        AnimalZoneSafehouseGuard.setEnabled(true);
        DesignationZoneAnimal existing = zone(42, 10, 10, 5, 6);
        Object pending = AnimalZoneSafehouseGuard.beforeParse(reader(true, false, 42.0));

        assertFalse(AnimalZoneSafehouseGuard.afterParse(pending, existing, new Object()));
        assertTrue(AnimalZoneSafehouseGuard.isBroken());
        assertNull(AnimalZoneSafehouseGuard.beforeParse(reader(true, false, 42.0)));
    }

    private static ByteBufferReader reader(boolean designation, boolean added, double id) {
        ByteBuffer bb = ByteBuffer.allocate(64);
        bb.put((byte) 9);
        bb.put((byte) (designation ? 1 : 0));
        bb.put((byte) (added ? 1 : 0));
        bb.putDouble(id);
        bb.flip();
        bb.position(1);
        return new ByteBufferReader(bb);
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countHelperCalls(byte[] classBytes, String method) {
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
                                        if (HELPER_CLASS.equals(owner)) {
                                            hits[0]++;
                                        }
                                    }
                                };
                            }
                        },
                        0);
        return hits[0];
    }
}
