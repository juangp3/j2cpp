package se.arnetheduck.j2c.test;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

public class Java17Lambdas {
	public interface Action {
		void run() throws Exception;
	}

	public interface Factory<T> {
		T create(int size);
	}

	private int base = 10;

	public static int apply(IntBinaryOperator op, int a, int b) {
		return op.applyAsInt(a, b);
	}

	public static <T, R> R map(Function<? super T, ? extends R> f, T value) {
		return f.apply(value);
	}

	public int expressionBody() {
		return apply((a, b) -> a * b + base, 2, 3);
	}

	public int blockBody(int offset) {
		IntBinaryOperator op = (a, b) -> {
			int sum = a + b;
			return sum + offset;
		};
		return op.applyAsInt(1, 2);
	}

	public int nested() {
		Function<Integer, Function<Integer, Integer>> adder = a -> b -> a + b + this.base;
		return adder.apply(1).apply(2);
	}

	// toString() inside the lambda must still mean Java17Lambdas.toString()
	public Supplier<String> describe() {
		return () -> toString() + hashCode();
	}

	@Override
	public String toString() {
		return "lambdas";
	}

	public Integer wildcards() {
		// This package declares its own Object class
		Function<java.lang.Object, Integer> hash = value -> value.hashCode();
		return map(hash, "x");
	}

	public Action action() {
		return () -> {
			throw new Exception("checked");
		};
	}

	public static int staticReference() {
		IntBinaryOperator max = Math::max;
		return max.applyAsInt(3, 4);
	}

	public static int unboundReference() {
		Function<String, Integer> length = String::length;
		return length.apply("four");
	}

	public Supplier<String> boundReference(String text) {
		return text::trim;
	}

	public Supplier<String> thisReference() {
		return this::toString;
	}

	public static StringBuilder constructorReference() {
		Function<String, StringBuilder> create = StringBuilder::new;
		return create.apply("x");
	}

	public static int[] arrayConstructorReference() {
		Factory<int[]> create = int[]::new;
		return create.create(3);
	}

	public static int comparatorWithDefaults(String a, String b) {
		Comparator<String> byLength = (x, y) -> x.length() - y.length();
		return byLength.compare(a, b);
	}

	public static <A, B> BiFunction<A, B, String> pair() {
		return (a, b) -> a + ":" + b;
	}

	// A generic functional method can only be implemented by a reference
	public interface Identity {
		<T> T apply(T value);
	}

	public static <T> T same(T value) {
		return value;
	}

	public static Identity genericReference() {
		return Java17Lambdas::same;
	}

	public static Runnable serializableTask(int[] counter) {
		return (Runnable & java.io.Serializable) () -> counter[0]++;
	}

	public static boolean isSerializable(Runnable task) {
		return task instanceof java.io.Serializable;
	}

	// Targets with captured wildcards under "? super", found in commons-lang:
	// the generated class implements Consumer<Object> and Consumer<E>
	public static int countAll(java.util.List<?> items) {
		int[] count = { 0 };
		items.forEach(item -> count[0]++);
		return count[0];
	}

	public static <E> void copyInto(java.util.Collection<? extends E> from,
			java.util.Collection<E> to) {
		from.forEach(element -> to.add(element));
	}

	// A bound receiver need not be effectively final: Java evaluates it once,
	// when the reference is created (found in Forge)
	public static class Tally {
		public int total;

		public void add(int value) {
			total += value;
		}
	}

	public interface IntSink {
		void accept(int value);
	}

	public static int reassignedReceiver() {
		Tally tally = new Tally();
		IntSink first = tally::add;
		tally = new Tally();
		first.accept(5);
		IntSink second = tally::add;
		second.accept(2);
		return tally.total;
	}

	// A lambda in a field initializer that uses the enclosing instance
	// (found in williamfiset/algorithms)
	private final IntBinaryOperator plusBase = (a, b) -> a + b + base;

	public int fieldInitializerLambda() {
		return plusBase.applyAsInt(1, 2);
	}

	// A default method that implements a generic superinterface method needs
	// a bridge in the interface (found in jsemver)
	public interface Check<T> {
		boolean test(T value);
	}

	public interface PositiveTally extends Check<Tally> {
		default boolean test(Tally tally) {
			return tally.total > 0;
		}
	}

	public static class AnyPositive implements PositiveTally {
	}

	public static boolean bridgedDefault(int value) {
		Check<Tally> check = new AnyPositive();
		Tally tally = new Tally();
		tally.add(value);
		return check.test(tally);
	}

	// Lambdas in initializer blocks capture the blocks' locals (found in
	// commons-codec); the instance block's lambda also needs this
	public static int staticTotal;

	static {
		int seed = 40;
		IntSink add = value -> staticTotal += value + seed;
		add.accept(2);
	}

	private int instanceTotal;

	{
		int offset = 5;
		IntSink set = value -> instanceTotal = value + offset;
		set.accept(1);
	}

	public static int staticInitializerCapture() {
		return staticTotal;
	}

	public int instanceInitializerCapture() {
		return instanceTotal;
	}

	// An enhanced for over a new expression needs parentheses before
	// ->iterator() in C++ (found in javapoet)
	public static class Countdown implements Iterable<Tally> {
		private final int from;

		public Countdown(int from) {
			this.from = from;
		}

		@Override
		public java.util.Iterator<Tally> iterator() {
			return new java.util.Iterator<Tally>() {
				private int next = from;

				@Override
				public boolean hasNext() {
					return next > 0;
				}

				@Override
				public Tally next() {
					Tally tally = new Tally();
					tally.add(next--);
					return tally;
				}
			};
		}
	}

	public static int iterateNewIterable(int from) {
		int sum = 0;
		for (Tally tally : new Countdown(from)) {
			sum += tally.total;
		}
		return sum;
	}

	// A subclass that re-implements an interface inherits a covariant return
	// naming the subclass itself; the base's C++ signature returns the base
	// type, so the generated forwarder must downcast (found in RoaringBitmap)
	public interface Limited {
		Limited limit(int n);
	}

	public interface MoreLimited extends Limited {
	}

	public static class LimitBase implements Limited {
		public int size;

		public LimitDerived limit(int n) {
			LimitDerived d = new LimitDerived();
			d.size = n;
			return d;
		}
	}

	public static class LimitDerived extends LimitBase implements MoreLimited {
	}

	public static int inheritedCovariantReturn(int n) {
		MoreLimited m = new LimitDerived();
		return ((LimitDerived) m.limit(n)).size;
	}

	// A default method overriding an abstract superinterface method is its
	// only implementation, so the interface must declare it (commons-lang
	// Mutable.get overriding Supplier.get)
	public interface Source<T> {
		T get();
	}

	public interface Holder<T> extends Source<T> {
		@Override
		default T get() {
			return getValue();
		}

		T getValue();
	}

	public static class TallyHolder implements Holder<Tally> {
		public Tally tally = new Tally();

		public Tally getValue() {
			return tally;
		}
	}

	public static int defaultOverridesAbstract(int value) {
		Source<Tally> source = new TallyHolder();
		source.get().add(value);
		return source.get().total;
	}

	// A record accessor that implements an interface method must override it
	// (Forge's DefaultKeywordView implementing KeywordView)
	public interface Sized {
		int size();
	}

	public record Box(int size) implements Sized {
	}

	public static int recordAccessorOverride(int size) {
		Sized sized = new Box(size);
		return sized.size();
	}
}
