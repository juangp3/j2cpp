package java.util.concurrent;

import java.util.Map;

/**
 * ConcurrentMap. The JDK redeclares putIfAbsent, remove and replace here;
 * ConcurrentHashMap implements them all, and Map's defaults stand for other
 * implementations.
 */
public interface ConcurrentMap<K, V> extends Map<K, V> {
}
