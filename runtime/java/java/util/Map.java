package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Map<K, V> {
	int size();

	boolean isEmpty();

	boolean containsKey(Object key);

	boolean containsValue(Object value);

	V get(Object key);

	V put(K key, V value);

	V remove(Object key);

	void putAll(Map<? extends K, ? extends V> m);

	void clear();

	Set<K> keySet();

	Collection<V> values();

	Set<Map.Entry<K, V>> entrySet();

	boolean equals(Object o);

	int hashCode();

	interface Entry<K, V> {
		K getKey();

		V getValue();

		V setValue(V value);

		boolean equals(Object o);

		int hashCode();

		static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> comparingByKey() {
			return new Comparator<Map.Entry<K, V>>() {
				public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
					return a.getKey().compareTo(b.getKey());
				}
			};
		}

		static <K, V extends Comparable<? super V>> Comparator<Map.Entry<K, V>> comparingByValue() {
			return new Comparator<Map.Entry<K, V>>() {
				public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
					return a.getValue().compareTo(b.getValue());
				}
			};
		}
	}

	default V getOrDefault(Object key, V defaultValue) {
		V v = get(key);
		return v != null || containsKey(key) ? v : defaultValue;
	}

	default void forEach(BiConsumer<? super K, ? super V> action) {
		Objects.requireNonNull(action);
		for (Map.Entry<K, V> entry : entrySet()) {
			action.accept(entry.getKey(), entry.getValue());
		}
	}

	default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		Objects.requireNonNull(function);
		for (Map.Entry<K, V> entry : entrySet()) {
			entry.setValue(function.apply(entry.getKey(), entry.getValue()));
		}
	}

	default V putIfAbsent(K key, V value) {
		V v = get(key);
		if (v == null) {
			v = put(key, value);
		}
		return v;
	}

	default boolean remove(Object key, Object value) {
		Object current = get(key);
		if (!Objects.equals(current, value) || (current == null && !containsKey(key))) {
			return false;
		}
		remove(key);
		return true;
	}

	default boolean replace(K key, V oldValue, V newValue) {
		Object current = get(key);
		if (!Objects.equals(current, oldValue) || (current == null && !containsKey(key))) {
			return false;
		}
		put(key, newValue);
		return true;
	}

	default V replace(K key, V value) {
		V current = get(key);
		if (current != null || containsKey(key)) {
			current = put(key, value);
		}
		return current;
	}

	default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V v = get(key);
		if (v == null) {
			V newValue = mappingFunction.apply(key);
			if (newValue != null) {
				put(key, newValue);
				return newValue;
			}
		}
		return v;
	}

	default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction);
		V oldValue = get(key);
		if (oldValue != null) {
			V newValue = remappingFunction.apply(key, oldValue);
			if (newValue != null) {
				put(key, newValue);
				return newValue;
			}
			remove(key);
		}
		return null;
	}

	default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction);
		V oldValue = get(key);
		V newValue = remappingFunction.apply(key, oldValue);
		if (newValue == null) {
			if (oldValue != null || containsKey(key)) {
				remove(key);
			}
			return null;
		}
		put(key, newValue);
		return newValue;
	}

	default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction);
		Objects.requireNonNull(value);
		V oldValue = get(key);
		V newValue = oldValue == null ? value : remappingFunction.apply(oldValue, value);
		if (newValue == null) {
			remove(key);
		} else {
			put(key, newValue);
		}
		return newValue;
	}

	static <K, V> Map<K, V> of() {
		return Collections.emptyMap();
	}

	static <K, V> Map<K, V> of(K k1, V v1) {
		return ofEntries(entry(k1, v1));
	}

	static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
		return ofEntries(entry(k1, v1), entry(k2, v2));
	}

	static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
		return ofEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3));
	}

	static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
		return ofEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4));
	}

	@SafeVarargs
	static <K, V> Map<K, V> ofEntries(Map.Entry<? extends K, ? extends V>... entries) {
		Map<K, V> map = new LinkedHashMap<>();
		for (Map.Entry<? extends K, ? extends V> e : entries) {
			if (map.put(e.getKey(), e.getValue()) != null) {
				throw new IllegalArgumentException("duplicate key: " + e.getKey());
			}
		}
		return Collections.unmodifiableMap(map);
	}

	static <K, V> Map.Entry<K, V> entry(K k, V v) {
		return new AbstractMap.SimpleImmutableEntry<>(Objects.requireNonNull(k), Objects.requireNonNull(v));
	}

	static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(map));
	}
}
