package se.arnetheduck.j2c.test;

/**
 * Interfaces that --mocks turns into gMock classes, and translated code that
 * calls them; test/cpp/Java17MocksTest.cpp passes the mocks to that code.
 */
public class Java17Mocks {
	public static class Item {
	}

	/** Overloads, arrays, varargs and a C++ keyword */
	public interface Counter {
		int next();

		void add(int amount);

		void add(int amount, boolean twice);

		long total(long[] values, double scale, char separator, java.lang.Object... rest);

		void delete(int and);
	}

	/** A generic interface, a generic method, a default and a static method */
	public interface Source<T> {
		T get(int index);

		<N extends Number> N convert(T item, N fallback);

		default boolean has(int index) {
			return get(index) != null;
		}

		static int none() {
			return -1;
		}
	}

	public interface Named {
		String name();
	}

	/** Inherited methods, from two superinterfaces, and a covariant return */
	public interface ItemSource extends Source<Item>, Named {
		@Override
		Item get(int index);
	}

	public interface Sink<T> {
		void accept(T item);
	}

	/** Overrides accept(T) with another erasure: the mock has a bridge */
	public interface ItemSink extends Sink<Item> {
		@Override
		void accept(Item item);
	}

	/** A default method implementing an inherited abstract one */
	public interface Fixed extends Counter {
		@Override
		default int next() {
			return 42;
		}

		int last();
	}

	public interface Leveled {
		default int level() {
			return 7;
		}
	}

	public interface Ranked {
		int level(int bonus);
	}

	/** level() and level(int) from two bases: C++ redeclares both as pure */
	public interface Graded extends Leveled, Ranked {
	}

	/** Fifteen parameters, the most gMock's MOCK_METHOD takes */
	public interface Fifteen {
		int call(int a0, int a1, int a2, int a3, int a4, int a5, int a6,
				int a7, int a8, int a9, int a10, int a11, int a12, int a13,
				int a14);
	}

	public interface Numbered<T extends Number> {
		T value();
	}

	@SuppressWarnings("rawtypes")
	public interface Compared {
		Comparable value();
	}

	/**
	 * value() erases to Number and to Comparable in the bases; declaring it
	 * with Integer gives the mock a C++ return type that overrides both
	 */
	public interface Measured extends Numbered<Integer>, Compared {
		@Override
		Integer value();
	}

	public static int sumNext(Counter counter, int times) {
		int sum = 0;
		for (int i = 0; i < times; ++i) {
			sum += counter.next();
		}
		counter.add(sum);
		counter.add(sum, true);
		counter.delete(times);
		return sum;
	}

	public static <T> int present(Source<T> source, int count) {
		int found = 0;
		for (int i = 0; i < count; ++i) {
			if (source.has(i)) {
				++found;
			}
		}
		return found;
	}

	public static java.lang.Object first(Source<Item> source) {
		return source.get(0);
	}

	public static <T> void feed(Sink<T> sink, T item) {
		sink.accept(item);
	}

	public static int nextAndLast(Fixed fixed) {
		return fixed.next() + fixed.last();
	}

	public static int grade(Graded graded) {
		return graded.level() * 10 + graded.level(3);
	}
}
