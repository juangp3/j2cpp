// expect: compiles
package probes;
import java.util.*;
import java.util.function.*;
public class Generics {
	static <T extends Comparable<? super T>> T max(List<? extends T> xs) { T best = null; for (T x : xs) if (best == null || x.compareTo(best) > 0) best = x; return best; }
	static <K, V> Map<V, K> invert(Map<K, V> m) { Map<V, K> r = new HashMap<>(); for (Map.Entry<K, V> e : m.entrySet()) r.put(e.getValue(), e.getKey()); return r; }
	static int streams(List<String> xs) { return xs.stream().filter(s -> !s.isEmpty()).mapToInt(String::length).sum(); }
	static List<Integer> sorted(List<Integer> xs) { List<Integer> c = new ArrayList<>(xs); c.sort(Comparator.reverseOrder()); return c; }
	static Optional<String> first(List<String> xs) { return xs.stream().findFirst().map(String::trim); }
	static <T> Supplier<List<T>> factory() { return ArrayList::new; }
	static int varargs(int... xs) { return xs.length; }
	static int callVarargs() { return varargs() + varargs(1, 2); }
	static int localVarLambda() { BiFunction<Integer, Integer, Integer> f = (var a, var b) -> a + b; return f.apply(1, 2); }
}
