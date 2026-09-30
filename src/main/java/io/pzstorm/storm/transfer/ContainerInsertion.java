package io.pzstorm.storm.transfer;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.inventory.StormInventoryWeight;
import io.pzstorm.storm.patch.fixes.ContainerChainGuard;
import java.util.function.BiFunction;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;

/** Main-thread insertion transaction. No network messages or equipment changes until success. */
final class ContainerInsertion {
    private ContainerInsertion() {}

    static boolean canInsert(ItemContainer source, ItemContainer destination, InventoryItem item) {
        if (item == null
                || destination == null
                || source == destination
                || (source == null && item.getContainer() != null)
                || (source != null
                        && (!ContainerChainGuard.validParents(source)
                                || item.getContainer() != source
                                || identityIndex(source, item) < 0))
                || ContainerChainGuard.wouldNest(destination, item)) return false;
        // Vanilla returns an existing item with the same ID as a successful-looking result.
        return !destination.containsID(item.getID())
                && ContainerChainGuard.validTree(destination, null)
                && (source == null || ContainerChainGuard.validTree(source, null));
    }

    static boolean insert(ItemContainer source, ItemContainer destination, InventoryItem item) {
        return insert(source, destination, item, ItemContainer::AddItem);
    }

    static boolean insert(
            ItemContainer source,
            ItemContainer destination,
            InventoryItem item,
            BiFunction<ItemContainer, InventoryItem, InventoryItem> add) {
        if (!canInsert(source, destination, item)) return false;
        ItemContainer previous = item.getContainer();
        int sourceIndex = source == null ? -1 : identityIndex(source, item);
        try {
            InventoryItem result = add.apply(destination, item); // Vanilla owns source removal.
            if (result == item
                    && item.getContainer() == destination
                    && identityIndex(destination, item) >= 0
                    && (source == null || identityIndex(source, item) < 0)) return true;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Container insertion failed; restoring source for item {}",
                    item.getID(),
                    failure);
        }
        // Refusal or an exception after partial insertion: restore the original list membership.
        // Direct list access is intentional: calling AddItem again would re-enter the failing hook.
        destination.getItems().removeIf(candidate -> candidate == item);
        if (source != null && identityIndex(source, item) < 0) {
            source.getItems().add(Math.min(sourceIndex, source.getItems().size()), item);
        }
        item.setContainer(previous);
        destination.setDrawDirty(true);
        if (source != null) source.setDrawDirty(true);
        StormInventoryWeight.bumpContainer(destination);
        if (source != null) StormInventoryWeight.bumpContainer(source);
        return false;
    }

    static int identityIndex(ItemContainer container, InventoryItem item) {
        var items = container.getItems();
        for (int i = 0; i < items.size(); i++) if (items.get(i) == item) return i;
        return -1;
    }
}
