import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

/** Offline policy inspection only: never invokes enforcement or changes policy. */
public final class InspectGameSecurity {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("zombie.network.anticheats.SDAC");
        for (String name : new String[] {"DEFAULT_POLICY", "POLICIES"}) {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            System.out.println(name + "=" + (value instanceof Map<?, ?> map
                    ? new TreeMap<>(map) : value));
        }
    }
}
