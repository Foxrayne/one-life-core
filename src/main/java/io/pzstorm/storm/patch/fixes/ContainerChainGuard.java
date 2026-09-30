package io.pzstorm.storm.patch.fixes;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.metrics.ContainerChainGuardMetrics;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;

/**
 * Upstream 3659e9f container guards, with bounded, non-destructive corruption handling. A failed
 * lookup never repairs either client's or server's inventory behind a getter. Invalid structures
 * remain available for explicit recovery; insertion and serialization refuse them instead of
 * orphaning items or recursing. Budget exhaustion is not reported as a cycle.
 */
public final class ContainerChainGuard {
    public static final Object NONE = new Object();
    static final int MAX_DEPTH = 1024;
    static final int MAX_TREE_ITEMS = 65536;
    static final int MAX_TREE_DEPTH = 128;
    private static final ThreadLocal<ArrayDeque<IdentityHashMap<ItemContainer, Boolean>>> saving =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicLong nextWarning = new AtomicLong();

    private ContainerChainGuard() {}

    public static void failed(String reason) {
        ContainerChainGuardMetrics.recordBlockedLookup();
        long now = System.nanoTime(), due = nextWarning.get();
        if (now >= due && nextWarning.compareAndSet(due, now + 30_000_000_000L)) {
            LOGGER.warn(
                    "ContainerChainGuard: {}; operation refused, items retained for recovery",
                    reason);
        }
    }

    /** Floyd cycle detection keeps common getters allocation-free. -1 means invalid/excessive. */
    private static int chainLength(ItemContainer start, boolean stopAtFloor) {
        ItemContainer slow = start, fast = start;
        int count = 0;
        while (slow != null && !(stopAtFloor && "floor".equals(slow.getType()))) {
            if (++count > MAX_DEPTH) {
                failed("parent depth budget exceeded");
                return -1;
            }
            slow = step(slow, stopAtFloor);
            fast = step(step(fast, stopAtFloor), stopAtFloor);
            if (slow != null && slow == fast) {
                failed("cyclic parent chain");
                return -1;
            }
        }
        return count;
    }

    private static ItemContainer step(ItemContainer c, boolean stopAtFloor) {
        return c == null || (stopAtFloor && "floor".equals(c.getType())) ? null : outer(c);
    }

    public static Object getCharacter(Object ref) {
        ItemContainer c = (ItemContainer) ref;
        int length = chainLength(c, false);
        for (int i = 0; i < length; i++, c = outer(c)) {
            if (c.getParent() instanceof IsoGameCharacter chr) return chr;
        }
        return NONE;
    }

    public static Object getOutermostContainer(Object ref) {
        ItemContainer c = (ItemContainer) ref;
        int length = chainLength(c, false);
        if (length <= 0) return NONE;
        for (int i = 1; i < length; i++) c = outer(c);
        return c;
    }

    public static Object getOutermostContainerOfItem(Object ref) {
        ItemContainer c = ((InventoryItem) ref).getContainer();
        int length = chainLength(c, true);
        if (length <= 0) return NONE;
        for (int i = 1; i < length; i++) c = outer(c);
        return c;
    }

    public static int isInside(Object ref, Object item) {
        if (item == null) return 1;
        ItemContainer c = (ItemContainer) ref;
        int length = chainLength(c, false);
        for (int i = 0; i < length; i++, c = outer(c)) if (c.getContainingItem() == item) return 2;
        return 1;
    }

    public static int isInCharacterInventory(Object ref, Object character) {
        if (!(character instanceof IsoGameCharacter chr)) return 1;
        ItemContainer c = (ItemContainer) ref;
        int length = chainLength(c, false);
        for (int i = 0; i < length; i++, c = outer(c)) if (c == chr.getInventory()) return 2;
        // Do not re-enter vanilla's recursive contains on inconsistent parent/child links.
        return 1;
    }

    /** Includes malformed destination chains and child graphs, not just direct self-insertion. */
    public static boolean wouldNest(Object targetRef, Object itemRef) {
        if (!(targetRef instanceof ItemContainer target)
                || !(itemRef instanceof InventoryItem item)) return false;
        int length = chainLength(target, false);
        boolean refused = length < 0;
        if (!refused && item instanceof InventoryContainer bag) {
            ItemContainer c = target;
            for (int i = 0; i < length; i++, c = outer(c))
                if (c == bag.getInventory() || c.getContainingItem() == bag) {
                    refused = true;
                    break;
                }
            if (!refused) refused = !validTree(bag.getInventory(), target);
        }
        if (refused) {
            ContainerChainGuardMetrics.recordRefusedNesting();
            failed("unsafe container insertion");
        }
        return refused;
    }

    public static boolean validParents(ItemContainer container) {
        return chainLength(container, false) >= 0;
    }

    private record Node(ItemContainer container, int depth) {}

    /** Identity-based bounded traversal; also catches duplicate references and broken backlinks. */
    public static boolean validTree(ItemContainer root, ItemContainer forbidden) {
        if (root == null) return false;
        return validTree(root, forbidden, new IdentityHashMap<>());
    }

    private static boolean validTree(
            ItemContainer root,
            ItemContainer forbidden,
            IdentityHashMap<ItemContainer, Boolean> containers) {
        var stack = new ArrayDeque<Node>();
        var items = new IdentityHashMap<InventoryItem, Boolean>();
        stack.push(new Node(root, 0));
        int count = 0;
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            ItemContainer c = node.container();
            if (c == forbidden
                    || node.depth() > MAX_TREE_DEPTH
                    || containers.put(c, Boolean.TRUE) != null) return false;
            if (c.getItems() == null || c.getItems().size() > MAX_TREE_ITEMS - count) return false;
            for (InventoryItem item : c.getItems()) {
                if (++count > MAX_TREE_ITEMS
                        || item == null
                        || items.put(item, Boolean.TRUE) != null
                        || item.getContainer() != c) return false;
                if (item instanceof InventoryContainer bag) {
                    ItemContainer child = bag.getInventory();
                    if (child == null || child.getContainingItem() != bag) return false;
                    stack.push(new Node(child, node.depth() + 1));
                }
            }
        }
        return true;
    }

    /** Checks once per synchronous save tree, including both save overloads and nested bags. */
    public static boolean beginSave(Object ref) {
        ItemContainer container = (ItemContainer) ref;
        var scopes = saving.get();
        if (!scopes.isEmpty() && scopes.peek().containsKey(container)) return false;
        var checked = new IdentityHashMap<ItemContainer, Boolean>();
        if (container == null || !validTree(container, null, checked)) {
            if (scopes.isEmpty()) saving.remove();
            failed("corrupt or over-budget inventory serialization");
            throw new IllegalStateException(
                    "Unsafe inventory tree: save refused; items retained for recovery");
        }
        scopes.push(checked);
        return true;
    }

    public static void endSave(boolean owner) {
        if (!owner) return;
        var scopes = saving.get();
        scopes.pop();
        if (scopes.isEmpty()) saving.remove();
    }

    public static void requireSerializable(Object ref) {
        boolean owner = beginSave(ref);
        endSave(owner);
    }

    static ItemContainer outer(ItemContainer c) {
        InventoryItem holder = c.getContainingItem();
        return holder == null ? null : holder.getContainer();
    }
}
