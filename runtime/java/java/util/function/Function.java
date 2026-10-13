package java.util.function;

@FunctionalInterface
public interface Function<T, R> {
	R apply(T t);

	static <T> Function<T, T> identity() {
		return t -> t;
	}
}
