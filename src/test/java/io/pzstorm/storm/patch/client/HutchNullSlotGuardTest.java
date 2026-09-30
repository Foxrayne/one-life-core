package io.pzstorm.storm.patch.client;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import io.pzstorm.storm.advice.client.hutchnullslot.HutchNullSlotGuard;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;
import zombie.iso.objects.IsoHutch;

class HutchNullSlotGuardTest implements UnitTest {
    private long savedStripped;
    private long savedFailed;

    @BeforeEach
    void resetCounters() {
        savedStripped = HutchNullSlotGuard.STRIPPED.getAndSet(0);
        savedFailed = HutchNullSlotGuard.FAILED.getAndSet(0);
    }

    @AfterEach
    void restoreCounters() {
        HutchNullSlotGuard.STRIPPED.set(savedStripped);
        HutchNullSlotGuard.FAILED.set(savedFailed);
    }

    @Test
    void healthyHutchDoesNotEnterWarningPath() {
        var animals = new HashMap<Integer, Object>();
        Object animal = new Object();
        animals.put(1, animal);
        long failures = HutchNullSlotGuard.FAILED.get();
        long stripped = HutchNullSlotGuard.STRIPPED.get();
        // No hutch needed unless the warning branch runs. Previously this tried to
        // dereference it even when zero slots were removed.
        HutchNullSlotGuard.stripNullSlots(null, animals);
        assertSame(animal, animals.get(1));
        assertEquals(stripped, HutchNullSlotGuard.STRIPPED.get());
        assertEquals(failures, HutchNullSlotGuard.FAILED.get());
    }

    @Test
    void removesOnlyNullSlotsAndIsIdempotent() throws Exception {
        IsoHutch hutch =
                (IsoHutch)
                        ReflectionFactory.getReflectionFactory()
                                .newConstructorForSerialization(
                                        IsoHutch.class, Object.class.getDeclaredConstructor())
                                .newInstance();
        var animals = new HashMap<Integer, Object>();
        Object animal = new Object();
        animals.put(0, null);
        animals.put(1, animal);
        animals.put(2, null);
        long failures = HutchNullSlotGuard.FAILED.get();
        long stripped = HutchNullSlotGuard.STRIPPED.get();
        HutchNullSlotGuard.stripNullSlots(hutch, animals);
        HutchNullSlotGuard.stripNullSlots(hutch, animals);
        assertEquals(1, animals.size());
        assertSame(animal, animals.get(1));
        assertEquals(stripped + 2, HutchNullSlotGuard.STRIPPED.get());
        assertEquals(failures, HutchNullSlotGuard.FAILED.get());
    }
}
