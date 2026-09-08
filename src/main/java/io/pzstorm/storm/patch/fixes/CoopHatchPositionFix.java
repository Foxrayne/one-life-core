package io.pzstorm.storm.patch.fixes;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.util.ArrayList;
import zombie.characters.animals.IsoAnimal;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.AnimalInventoryItem;
import zombie.inventory.types.Food;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoHutch;

/**
 * Pure logic behind {@link CoopHatchPositionFixPatch}: give any origin-positioned animal entering a
 * hutch the hutch's own world coordinates.
 *
 * <h2>The bug this heals</h2>
 *
 * <p>{@code Food.checkEggHatch(IsoHutch)} declares {@code int x = 0, y = 0, z = 0} and only assigns
 * them inside its {@code hutch == null} branch (world item / container / vehicle / player). The one
 * caller that passes a non-null hutch — {@code IsoHutch.update()} hatching nest box eggs —
 * therefore constructs every chick as {@code new IsoAnimal(cell, 0, 0, 0, ...)}, i.e. at world
 * position {@code (0.5, 0.5, 0.0)}, the map origin. The {@code x == 0 && y == 0} sanity guard in
 * the same method cannot fire because it too lives inside the {@code hutch == null} branch.
 *
 * <p>The player-visible symptom is sync relevancy, not a chick standing at the corner. {@code
 * AnimalSynchronizationManager.sendUpdateToClient} gates every animal update on {@code
 * connection.RelevantTo(animal.getX(), animal.getY(), ...)}, so an origin-positioned chick never
 * syncs to its owner — the coop UI shows nothing and players report their chicks "gone" — while it
 * does sync, all at once, to any client that happens to stand near the map corner. The bad
 * coordinate outlives the coop: {@code IsoHutch.releaseAnimal} repairs it server-side (enter-spot
 * square before {@code addToWorld()}), but the grab-to-item path ({@code IsoHutch.removeAnimal} via
 * {@code AnimalCommandPacket.HutchGrabAnimal}) keeps the registry entry at origin while the chick
 * is boxed as a {@code Base.Animal} item, and {@code IsoAnimal.save} persists raw x/y/z across
 * restarts. Boxing round-trips safely — {@code DropAnimal} re-creates the animal at the drop
 * coordinates — but a registry entry whose item is lost strands and dies at origin. Measured on a
 * live 42.20.4 server: 128 chicks at exactly {@code (0.5, 0.5, 0.0)} — 116 alive (every one boxed
 * as an item in world containers) and 12 dead (8 died while boxed, 4 stranded unheld).
 *
 * <h2>The fix</h2>
 *
 * <p>Entry advice on {@code IsoHutch.addAnimalInside(IsoAnimal, boolean)} — the funnel every animal
 * passes through on its way into a hutch, covering both the hatch path and {@code IsoHutch.load}
 * re-adding saved animals (which also repairs chicks already saved at origin while still inside a
 * coop). An animal arriving with origin coordinates gets the hutch's enter-spot tile, the same tile
 * {@code releaseAnimal} uses as its fallback.
 *
 * <h2>The second hatch path</h2>
 *
 * <p>{@code Food.update()} calls {@code checkEggHatch(null)} for every egg that is not in a nest
 * box. That branch resolves x/y/z only for a world item, a vehicle trailer, or a player's own
 * inventory. An egg in any other container — a crate, a counter, a bag — keeps {@code (0, 0, 0)},
 * and because the container is not "floor" the code sets {@code inInv = true}, which also disables
 * the {@code x == 0 && y == 0} guard. The chick is boxed straight into that container as a {@code
 * Base.Animal} item positioned at the origin. It never enters a hutch, so the advice above cannot
 * see it. {@link #ensureContainerHatchPosition} closes this path from an exit advice on {@code
 * checkEggHatch}: the egg's container is captured on entry (the hatch removes the egg from it), and
 * on a successful non-hutch hatch every origin-positioned boxed animal in that container is moved
 * to the container's resolved world square.
 *
 * <p>The decision logic ({@link #needsFix}, {@link #isAtOrigin}) is split out from the I/O so it
 * can be unit-tested without game classes on the classpath.
 */
public final class CoopHatchPositionFix {

    private CoopHatchPositionFix() {}

    /**
     * Pure decision: does an animal at {@code (animalX, animalY)} entering a hutch anchored at
     * {@code (hutchSavedX, hutchSavedY)} need its position repaired?
     *
     * <p>An animal constructed at tile {@code (0, 0, 0)} sits at the square centre {@code (0.5,
     * 0.5)}, so the test is a {@code < 1} band, not an exact-zero compare. No legitimate hutch
     * exists near the map origin, and a hutch that itself reports {@code (0, 0)} has no better
     * position to offer.
     *
     * @param animalX the animal's current world x
     * @param animalY the animal's current world y
     * @param hutchSavedX the hutch's saved tile x
     * @param hutchSavedY the hutch's saved tile y
     * @return {@code true} if the animal should be moved to the hutch
     */
    public static boolean needsFix(float animalX, float animalY, int hutchSavedX, int hutchSavedY) {
        if (!isAtOrigin(animalX, animalY)) {
            return false;
        }
        return hutchSavedX != 0 || hutchSavedY != 0;
    }

    /**
     * Pure decision: is {@code (animalX, animalY)} inside the origin band that only a {@code (0, 0,
     * 0)}-constructed animal occupies?
     *
     * @param animalX the animal's current world x
     * @param animalY the animal's current world y
     * @return {@code true} if both axes are below one tile
     */
    public static boolean isAtOrigin(float animalX, float animalY) {
        return animalX < 1.0f && animalY < 1.0f;
    }

    /**
     * Driver called from the {@code IsoHutch.addAnimalInside(IsoAnimal, boolean)} entry advice.
     *
     * <p>Parameters are typed {@code Object} so the inlined advice does not embed checkcasts
     * against game classes into the patched method's bytecode; the casts happen here, on the first
     * actual call, when both classes are guaranteed loaded. See the {@code
     * feedback_elided_cast_load} memory.
     *
     * @param hutchRef the {@code IsoHutch} being entered
     * @param animalRef the {@code IsoAnimal} entering it
     */
    public static void ensurePosition(Object hutchRef, Object animalRef) {
        IsoHutch hutch = (IsoHutch) hutchRef;
        IsoAnimal animal = (IsoAnimal) animalRef;
        if (!needsFix(animal.getX(), animal.getY(), hutch.savedX, hutch.savedY)) {
            return;
        }
        int enterX = 0;
        int enterY = 0;
        try {
            enterX = hutch.getEnterSpotX();
            enterY = hutch.getEnterSpotY();
        } catch (RuntimeException e) {
            // def table not populated yet — the hutch tile itself is still a valid position
        }
        animal.setX(hutch.savedX + enterX);
        animal.setY(hutch.savedY + enterY);
        animal.setZ(hutch.savedZ);
        LOGGER.warn(
                "Repaired origin-positioned {} (id {}) entering hutch at {},{},{}",
                animal.getAnimalType(),
                animal.getOnlineID(),
                hutch.savedX,
                hutch.savedY,
                hutch.savedZ);
    }

    /**
     * Entry half of the {@code Food.checkEggHatch(IsoHutch)} advice: remember which container a
     * fertilized egg sits in, because a hatch removes the egg from it before the method returns.
     *
     * @param eggRef the {@code Food} whose hatch is being checked
     * @return the egg's {@code ItemContainer}, or {@code null} if the egg cannot hatch or is loose
     */
    public static Object captureHatchContainer(Object eggRef) {
        Food egg = (Food) eggRef;
        if (!egg.isFertilized()) {
            return null;
        }
        return egg.getContainer();
    }

    /**
     * Exit half of the {@code Food.checkEggHatch(IsoHutch)} advice. After a non-hutch hatch,
     * re-home every origin-positioned boxed animal in the egg's former container to that
     * container's world square.
     *
     * @param hutchRef the {@code IsoHutch} argument, non-null on the nest-box path this ignores
     * @param hatched the method's return value
     * @param containerRef the container captured by {@link #captureHatchContainer}
     */
    public static void ensureContainerHatchPosition(
            Object hutchRef, boolean hatched, Object containerRef) {
        if (!hatched || hutchRef != null || containerRef == null) {
            return;
        }
        ItemContainer container = (ItemContainer) containerRef;
        ArrayList<InventoryItem> items = container.getItems();
        IsoGridSquare square = null;
        boolean resolved = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            InventoryItem item = items.get(i);
            if (!(item instanceof AnimalInventoryItem)) {
                continue;
            }
            IsoAnimal animal = ((AnimalInventoryItem) item).getAnimal();
            if (animal == null || !isAtOrigin(animal.getX(), animal.getY())) {
                continue;
            }
            if (!resolved) {
                square = container.getSquare();
                resolved = true;
            }
            if (square == null) {
                LOGGER.warn(
                        "Origin-positioned {} (id {}) hatched in {} container with no resolvable square",
                        animal.getAnimalType(),
                        animal.getOnlineID(),
                        container.getType());
                return;
            }
            animal.setX(square.getX() + 0.5f);
            animal.setY(square.getY() + 0.5f);
            animal.setZ(square.getZ());
            LOGGER.warn(
                    "Repaired origin-positioned {} (id {}) hatched in {} container at {},{},{}",
                    animal.getAnimalType(),
                    animal.getOnlineID(),
                    container.getType(),
                    square.getX(),
                    square.getY(),
                    square.getZ());
        }
    }
}
