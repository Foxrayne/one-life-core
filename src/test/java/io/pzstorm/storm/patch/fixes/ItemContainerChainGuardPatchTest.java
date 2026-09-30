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
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;

/**
 * Verifies that {@link ItemContainerChainGuardPatch} and {@link
 * InventoryItemGetOutermostContainerPatch} route exactly the chain-walking methods through {@link
 * ContainerChainGuard}, and that the guard refuses cyclic operations without mutating inventory.
 */
class ItemContainerChainGuardPatchTest implements UnitTest {

    private static final String CONTAINER_CLASS = "zombie/inventory/ItemContainer";
    private static final String ITEM_CLASS = "zombie/inventory/InventoryItem";
    private static final String HELPER_CLASS = "io/pzstorm/storm/patch/fixes/ContainerChainGuard";
    private static final String ADD_ITEM_DESC =
            "(Lzombie/inventory/InventoryItem;)Lzombie/inventory/InventoryItem;";

    @Test
    void patchInjectsHelperIntoChainWalksOnly() throws Exception {
        byte[] raw = readClassBytes(CONTAINER_CLASS + ".class");
        byte[] transformed = new ItemContainerChainGuardPatch().transform(raw);
        assertNotNull(transformed);

        for (String method :
                new String[] {
                    "getCharacter", "getOutermostContainer", "isInside", "isInCharacterInventory"
                }) {
            assertEquals(0, countHelperCalls(raw, method, null), "vanilla " + method);
            assertTrue(countHelperCalls(transformed, method, null) >= 1, "patched " + method);
        }
        assertTrue(countHelperCalls(transformed, "AddItem", ADD_ITEM_DESC) >= 1);
        assertEquals(
                0,
                countHelperCalls(transformed, "AddItem", "(Ljava/lang/String;)"),
                "AddItem(String) creates a fresh item and needs no gate");
        assertEquals(0, countHelperCalls(transformed, "Remove", null));
        assertTrue(countHelperCalls(transformed, "save", null) >= 1);
    }

    @Test
    void itemPatchInjectsHelperIntoGetOutermostContainerOnly() throws Exception {
        byte[] raw = readClassBytes(ITEM_CLASS + ".class");
        byte[] transformed = new InventoryItemGetOutermostContainerPatch().transform(raw);
        assertNotNull(transformed);
        assertEquals(0, countHelperCalls(raw, "getOutermostContainer", null));
        assertTrue(countHelperCalls(transformed, "getOutermostContainer", null) >= 1);
        assertEquals(0, countHelperCalls(transformed, "getContainer", null));
    }

    @Test
    void walksTerminateOnATreeAndReportItsRoot() {
        ItemContainer root = new ItemContainer();
        InventoryContainer bag = newBag();
        bag.setContainer(root);
        root.getItems().add(bag);

        assertSame(ContainerChainGuard.NONE, ContainerChainGuard.getCharacter(bag.getInventory()));
        assertSame(root, ContainerChainGuard.getOutermostContainer(bag.getInventory()));
        assertSame(root, ContainerChainGuard.getOutermostContainer(root));
        assertEquals(2, ContainerChainGuard.isInside(bag.getInventory(), bag));
        assertEquals(1, ContainerChainGuard.isInside(root, bag));
        assertTrue(ContainerChainGuard.validParents(bag.getInventory()));
    }

    @Test
    void selfCycleIsBoundedWithoutDetachingOrLosingItems() {
        InventoryContainer bag = newBag();
        bag.setContainer(bag.getInventory());
        bag.getInventory().getItems().add(bag);
        assertSame(ContainerChainGuard.NONE, ContainerChainGuard.getCharacter(bag.getInventory()));
        assertSame(
                ContainerChainGuard.NONE,
                ContainerChainGuard.getOutermostContainer(bag.getInventory()));
        assertSame(bag.getInventory(), bag.getContainer());
        assertSame(bag, bag.getInventory().getItems().getFirst());
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ContainerChainGuard.requireSerializable(bag.getInventory()));
    }

    @Test
    void twoBagCycleDoesNotMutateEitherMirror() {
        InventoryContainer a = newBag(), b = newBag();
        a.setContainer(b.getInventory());
        b.getInventory().getItems().add(a);
        b.setContainer(a.getInventory());
        a.getInventory().getItems().add(b);
        assertSame(ContainerChainGuard.NONE, ContainerChainGuard.getOutermostContainerOfItem(a));
        assertSame(b.getInventory(), a.getContainer());
        assertSame(a.getInventory(), b.getContainer());
        assertTrue(ContainerChainGuard.wouldNest(a.getInventory(), newBag()));
    }

    @Test
    void seventyLevelValidChainStillResolvesTheActualRoot() {
        ItemContainer root = new ItemContainer(), tail = root;
        for (int i = 0; i < 70; i++) {
            InventoryContainer bag = newBag();
            bag.setContainer(tail);
            tail.getItems().add(bag);
            tail = bag.getInventory();
        }
        assertSame(root, ContainerChainGuard.getOutermostContainer(tail));
        assertTrue(ContainerChainGuard.validTree(root, null));
    }

    @Test
    void depthBudgetDoesNotReturnAFakeRoot() {
        ItemContainer root = new ItemContainer(), tail = root;
        for (int i = 0; i < ContainerChainGuard.MAX_DEPTH; i++) {
            InventoryContainer bag = newBag();
            bag.setContainer(tail);
            tail.getItems().add(bag);
            tail = bag.getInventory();
        }
        assertSame(ContainerChainGuard.NONE, ContainerChainGuard.getOutermostContainer(tail));
    }

    @Test
    void malformedChildGraphIsRefusedEvenWhenParentChainIsAcyclic() {
        InventoryContainer a = newBag(), b = newBag();
        a.getInventory().getItems().add(b);
        b.setContainer(a.getInventory());
        b.getInventory().getItems().add(a); // Deliberately no parent backlink on a.
        assertTrue(ContainerChainGuard.wouldNest(new ItemContainer(), a));
        assertFalse(ContainerChainGuard.validTree(a.getInventory(), null));
        assertNull(a.getContainer());
        assertTrue(b.getInventory().getItems().contains(a));
    }

    @Test
    void duplicateChildReferenceIsRefusedBeforeSerialization() {
        InventoryContainer a = newBag(), b = newBag();
        a.getInventory().getItems().add(b);
        a.getInventory().getItems().add(b);
        b.setContainer(a.getInventory());
        assertFalse(ContainerChainGuard.validTree(a.getInventory(), null));
    }

    @Test
    void addItemRefusesToNestABagInItself() {
        ItemContainer root = new ItemContainer();
        InventoryContainer outer = newBag();
        InventoryContainer inner = newBag();
        outer.setContainer(root);
        root.getItems().add(outer);
        inner.setContainer(outer.getInventory());
        outer.getInventory().getItems().add(inner);

        assertTrue(ContainerChainGuard.wouldNest(outer.getInventory(), outer));
        assertTrue(ContainerChainGuard.wouldNest(inner.getInventory(), outer));
        assertFalse(ContainerChainGuard.wouldNest(inner.getInventory(), newBag()));
        assertFalse(ContainerChainGuard.wouldNest(root, inner));
    }

    @Test
    void floorBoundaryAndNullQueriesKeepVanillaResults() {
        ItemContainer floor = new ItemContainer();
        floor.setType("floor");
        InventoryContainer bag = newBag();
        bag.setContainer(floor);
        floor.getItems().add(bag);
        assertSame(ContainerChainGuard.NONE, ContainerChainGuard.getOutermostContainerOfItem(bag));
        assertSame(
                bag.getInventory(),
                ContainerChainGuard.getOutermostContainerOfItem(nestedBag(bag)));
        assertEquals(1, ContainerChainGuard.isInside(new ItemContainer(), null));
    }

    @Test
    void saveScopeDoesNotLeakValidationPastExitOrAcrossIndependentInventories() {
        ItemContainer root = new ItemContainer();
        InventoryContainer bag = newBag();
        root.getItems().add(bag);
        bag.setContainer(root);
        boolean owner = ContainerChainGuard.beginSave(root);
        try {
            assertTrue(owner);
            assertFalse(ContainerChainGuard.beginSave(bag.getInventory()));
            ContainerChainGuard.endSave(false);
            InventoryContainer bad = newBag();
            bad.getInventory().getItems().add(bad);
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> ContainerChainGuard.beginSave(bad.getInventory()));
        } finally {
            ContainerChainGuard.endSave(owner);
        }
        root.getItems().add(bag);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> ContainerChainGuard.beginSave(root));
        root.getItems().removeLast();
        ContainerChainGuard.requireSerializable(root);
    }

    private static InventoryContainer nestedBag(InventoryContainer parent) {
        InventoryContainer child = newBag();
        child.setContainer(parent.getInventory());
        parent.getInventory().getItems().add(child);
        return child;
    }

    /**
     * The real constructor resolves a texture through {@code ZomboidFileSystem}, which is not set
     * up in a unit test, so the bag is allocated without it and given its own inventory by hand.
     */
    private static InventoryContainer newBag() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Unsafe unsafe = (Unsafe) f.get(null);
            InventoryContainer bag =
                    (InventoryContainer) unsafe.allocateInstance(InventoryContainer.class);
            setField(bag, "module", "Base");
            setField(bag, "type", "Bag");
            setField(bag, "fullType", "Base.Bag");
            ItemContainer inventory = new ItemContainer();
            inventory.containingItem = bag;
            bag.setItemContainer(inventory);
            return bag;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(InventoryItem item, String name, String value)
            throws ReflectiveOperationException {
        Field f = InventoryItem.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(item, value);
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countHelperCalls(byte[] classBytes, String method, String descPrefix) {
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
                                if (!method.equals(name)
                                        || (descPrefix != null
                                                && !descriptor.startsWith(descPrefix))) {
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
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return hits[0];
    }
}
