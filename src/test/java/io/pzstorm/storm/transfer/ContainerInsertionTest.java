package io.pzstorm.storm.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;

class ContainerInsertionTest implements UnitTest {
    @Test
    void rejectedInsertionNeverRemovesTheSource() throws Exception {
        ItemContainer source = new ItemContainer();
        InventoryItem item = item(source, 101);
        ItemContainer refusing = new ItemContainer();
        java.util.function.BiFunction<ItemContainer, InventoryItem, InventoryItem> operation =
                (container, value) -> {
                    assertSame(source, value.getContainer());
                    assertTrue(source.getItems().contains(value));
                    return null;
                };
        assertFalse(ContainerInsertion.insert(source, refusing, item, operation));
        assertSame(source, item.getContainer());
        assertEquals(1, source.getItems().size());
        assertTrue(refusing.getItems().isEmpty());
    }

    @Test
    void exceptionAfterPartialInsertionRestoresOriginalPosition() throws Exception {
        ItemContainer source = new ItemContainer();
        InventoryItem item = item(source, 102);
        item(source, 103);
        ItemContainer throwing = new ItemContainer();
        java.util.function.BiFunction<ItemContainer, InventoryItem, InventoryItem> operation =
                (container, value) -> {
                    source.getItems().remove(value);
                    container.getItems().add(value);
                    value.setContainer(container);
                    throw new IllegalStateException("injected hook failure");
                };
        assertFalse(ContainerInsertion.insert(source, throwing, item, operation));
        assertSame(source, item.getContainer());
        assertSame(item, source.getItems().getFirst());
        assertEquals(2, source.getItems().size());
        assertTrue(throwing.getItems().isEmpty());
    }

    @Test
    void duplicateIdDoesNotMoveOrSubstituteTheWrongItem() throws Exception {
        ItemContainer source = new ItemContainer(), dest = new ItemContainer();
        InventoryItem moving = item(source, 104), existing = item(dest, 104);
        assertFalse(ContainerInsertion.insert(source, dest, moving));
        assertSame(moving, source.getItems().getFirst());
        assertSame(existing, dest.getItems().getFirst());
    }

    @Test
    void unconfirmedSuccessIsRolledBack() throws Exception {
        ItemContainer source = new ItemContainer();
        InventoryItem item = item(source, 105);
        ItemContainer liar = new ItemContainer();
        java.util.function.BiFunction<ItemContainer, InventoryItem, InventoryItem> operation =
                (container, value) -> value;
        assertFalse(ContainerInsertion.insert(source, liar, item, operation));
        assertSame(source, item.getContainer());
        assertSame(item, source.getItems().getFirst());
    }

    @Test
    void floorRefusalLeavesItemUnownedAndAvailable() throws Exception {
        InventoryItem item = item(null, 106);
        ItemContainer refusing = new ItemContainer();
        java.util.function.BiFunction<ItemContainer, InventoryItem, InventoryItem> operation =
                (container, value) -> null;
        assertFalse(ContainerInsertion.insert(null, refusing, item, operation));
        assertNull(item.getContainer());
        assertTrue(refusing.getItems().isEmpty());
    }

    @Test
    void acceptedInsertionHasExactlyOneOwner() throws Exception {
        ItemContainer source = new ItemContainer();
        InventoryItem item = item(source, 107);
        ItemContainer dest = new ItemContainer();
        java.util.function.BiFunction<ItemContainer, InventoryItem, InventoryItem> operation =
                (container, value) -> {
                    value.getContainer().getItems().remove(value);
                    container.getItems().add(value);
                    value.setContainer(container);
                    return value;
                };
        assertTrue(ContainerInsertion.insert(source, dest, item, operation));
        assertSame(dest, item.getContainer());
        assertTrue(source.getItems().isEmpty());
        assertEquals(1, dest.getItems().size());
    }

    @Test
    void changedRelationshipsAreRevalidatedAtInsertion() throws Exception {
        ItemContainer source = new ItemContainer(), target = new ItemContainer();
        InventoryContainer bag =
                (InventoryContainer) unsafe().allocateInstance(InventoryContainer.class);
        bag.setItemContainer(new ItemContainer());
        bag.getInventory().containingItem = bag;
        bag.setContainer(source);
        source.getItems().add(bag);
        assertTrue(ContainerInsertion.canInsert(source, target, bag));
        target.containingItem = bag;
        assertFalse(ContainerInsertion.insert(source, target, bag));
        assertSame(source, bag.getContainer());
        assertSame(bag, source.getItems().getFirst());
    }

    @Test
    void floorPickupCommitsBeforeWorldRemovalAndRearmsProcessingBeforeBroadcast() throws Exception {
        java.util.List<String> calls = new java.util.ArrayList<>();
        try (var in =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "io/pzstorm/storm/transfer/StormTransferHandler.class")) {
            new net.bytebuddy.jar.asm.ClassReader(in.readAllBytes())
                    .accept(
                            new net.bytebuddy.jar.asm.ClassVisitor(
                                    net.bytebuddy.jar.asm.Opcodes.ASM9) {
                                @Override
                                public net.bytebuddy.jar.asm.MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String desc,
                                        String sig,
                                        String[] exceptions) {
                                    if (!name.equals("executeFloorPickup")) return null;
                                    return new net.bytebuddy.jar.asm.MethodVisitor(
                                            net.bytebuddy.jar.asm.Opcodes.ASM9) {
                                        @Override
                                        public void visitMethodInsn(
                                                int op,
                                                String owner,
                                                String method,
                                                String descriptor,
                                                boolean itf) {
                                            calls.add(method);
                                        }
                                    };
                                }
                            },
                            0);
        }
        assertTrue(calls.indexOf("insert") >= 0);
        assertTrue(calls.indexOf("RemoveItemFromMap") > calls.indexOf("insert"));
        assertTrue(calls.indexOf("addToProcessItems") > calls.indexOf("RemoveItemFromMap"));
        assertTrue(calls.indexOf("sendAddItemToContainer") > calls.indexOf("addToProcessItems"));
        assertTrue(calls.indexOf("complete") > calls.indexOf("sendAddItemToContainer"));
    }

    private static InventoryItem item(ItemContainer owner, int id) throws Exception {
        InventoryItem item = (InventoryItem) unsafe().allocateInstance(InventoryItem.class);
        Field f = InventoryItem.class.getDeclaredField("id");
        f.setAccessible(true);
        f.setInt(item, id);
        item.setContainer(owner);
        if (owner != null) owner.getItems().add(item);
        return item;
    }

    private static Unsafe unsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }
}
