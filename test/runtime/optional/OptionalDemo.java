import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;
import java.util.function.UnaryOperator;

/** Optional, its primitive variants, and the java.util.function defaults */
public class OptionalDemo {
	public static void main(String[] args) {
		optionals();
		primitiveOptionals();
		functions();
	}

	static void optionals() {
		Optional<String> some = Optional.of("java");
		Optional<String> none = Optional.empty();
		System.out.println(some + " " + none + " " + Optional.ofNullable(null));
		System.out.println(some.isPresent() + " " + some.isEmpty() + " " + none.isPresent() + " " + none.isEmpty());
		System.out.println(some.get() + " " + none.orElse("other") + " " + none.orElseGet(() -> "got"));
		System.out.println(some.map(String::length) + " " + none.map(String::length));
		System.out.println(some.map(s -> null) + " " + some.filter(s -> s.startsWith("j")) + " "
				+ some.filter(s -> s.startsWith("x")));
		System.out.println(some.flatMap(s -> Optional.of(s + "!")) + " " + none.or(() -> Optional.of("alt")));
		System.out.println(some.equals(Optional.of("java")) + " " + none.equals(Optional.empty()) + " "
				+ some.hashCode() + " " + none.hashCode());
		some.ifPresent(s -> System.out.println("present " + s));
		none.ifPresent(s -> System.out.println("never"));
		none.ifPresentOrElse(s -> System.out.println("never"), () -> System.out.println("empty action"));
		try {
			none.get();
		} catch (NoSuchElementException e) {
			System.out.println("get: " + e.getMessage());
		}
		try {
			none.orElseThrow();
		} catch (NoSuchElementException e) {
			System.out.println("orElseThrow: " + e.getMessage());
		}
		try {
			none.orElseThrow(() -> new IllegalStateException("custom"));
		} catch (IllegalStateException e) {
			System.out.println("orElseThrow(supplier): " + e.getMessage());
		}
		try {
			Optional.of(null);
		} catch (NullPointerException e) {
			System.out.println("of(null): NullPointerException");
		}
		try {
			some.flatMap(s -> null);
		} catch (NullPointerException e) {
			System.out.println("flatMap null: NullPointerException");
		}
	}

	static void primitiveOptionals() {
		OptionalInt i = OptionalInt.of(42);
		OptionalLong l = OptionalLong.of(-7L);
		OptionalDouble d = OptionalDouble.of(2.5);
		System.out.println(i + " " + l + " " + d);
		System.out.println(OptionalInt.empty() + " " + OptionalLong.empty() + " " + OptionalDouble.empty());
		System.out.println(i.getAsInt() + " " + l.getAsLong() + " " + d.getAsDouble());
		System.out.println(OptionalInt.empty().orElse(5) + " " + OptionalLong.empty().orElseGet(() -> 9L) + " "
				+ OptionalDouble.empty().orElse(0.5));
		System.out.println(i.equals(OptionalInt.of(42)) + " " + i.equals(OptionalInt.empty()) + " "
				+ OptionalDouble.of(Double.NaN).equals(OptionalDouble.of(Double.NaN)) + " "
				+ OptionalDouble.of(0.0).equals(OptionalDouble.of(-0.0)));
		System.out.println(i.hashCode() + " " + l.hashCode() + " " + d.hashCode() + " " + OptionalInt.empty().hashCode());
		i.ifPresent(v -> System.out.println("int " + v));
		OptionalDouble.empty().ifPresentOrElse(v -> System.out.println("never"), () -> System.out.println("no double"));
		try {
			OptionalLong.empty().getAsLong();
		} catch (NoSuchElementException e) {
			System.out.println("getAsLong: " + e.getMessage());
		}
		try {
			OptionalInt.empty().orElseThrow(() -> new IllegalArgumentException("none"));
		} catch (IllegalArgumentException e) {
			System.out.println("orElseThrow: " + e.getMessage());
		}
	}

	static void functions() {
		Predicate<String> longWord = s -> s.length() > 3;
		Predicate<String> startsJ = s -> s.startsWith("j");
		System.out.println(longWord.and(startsJ).test("java") + " " + longWord.and(startsJ).test("kotlin") + " "
				+ longWord.or(startsJ).test("js") + " " + longWord.negate().test("go") + " "
				+ Predicate.not(longWord).test("rust"));
		System.out.println(Predicate.isEqual("a").test("a") + " " + Predicate.isEqual(null).test(null) + " "
				+ Predicate.isEqual(null).test("x"));
		Function<Integer, Integer> plus1 = x -> x + 1;
		Function<Integer, Integer> times2 = x -> x * 2;
		System.out.println(plus1.andThen(times2).apply(5) + " " + plus1.compose(times2).apply(5) + " "
				+ Function.<Integer>identity().apply(9) + " " + UnaryOperator.<String>identity().apply("id"));
		BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
		System.out.println(add.andThen(x -> "sum=" + x).apply(2, 3));
		BinaryOperator<String> shorter = BinaryOperator.minBy((a, b) -> a.length() - b.length());
		BinaryOperator<String> longer = BinaryOperator.maxBy((a, b) -> a.length() - b.length());
		System.out.println(shorter.apply("ab", "cd") + " " + shorter.apply("abc", "d") + " " + longer.apply("ab", "cd")
				+ " " + longer.apply("a", "bcd"));
		StringBuilder log = new StringBuilder();
		Consumer<String> first = s -> log.append("1").append(s);
		Consumer<String> both = first.andThen(s -> log.append("2").append(s));
		both.accept("x");
		BiConsumer<String, Integer> bc = (s, n) -> log.append(s).append(n);
		bc.andThen((s, n) -> log.append("/")).accept("k", 3);
		System.out.println(log);
		BiPredicate<String, Integer> hasLength = (s, n) -> s.length() == n;
		System.out.println(hasLength.test("abc", 3) + " " + hasLength.negate().test("abc", 3) + " "
				+ hasLength.and((s, n) -> n > 5).test("abc", 3) + " " + hasLength.or((s, n) -> n > 5).test("ab", 9));
		IntPredicate even = n -> n % 2 == 0;
		IntPredicate positive = n -> n > 0;
		System.out.println(even.and(positive).test(4) + " " + even.and(positive).test(-4) + " " + even.or(positive).test(-3)
				+ " " + even.negate().test(3));
		LongPredicate big = n -> n > 1000000000000L;
		System.out.println(big.test(1L << 41) + " " + big.negate().test(5L));
		IntUnaryOperator inc = n -> n + 1;
		IntUnaryOperator sq = n -> n * n;
		System.out.println(inc.andThen(sq).applyAsInt(3) + " " + inc.compose(sq).applyAsInt(3) + " "
				+ IntUnaryOperator.identity().applyAsInt(7));
		LongUnaryOperator neg = n -> -n;
		System.out.println(neg.andThen(n -> n * 10).applyAsLong(4));
		DoubleUnaryOperator half = x -> x / 2;
		System.out.println(half.andThen(Math::sqrt).applyAsDouble(32.0) + " " + DoubleUnaryOperator.identity().applyAsDouble(1.25));
		IntBinaryOperator max = Math::max;
		System.out.println(max.applyAsInt(3, 9));
		StringBuilder ints = new StringBuilder();
		IntConsumer a = n -> ints.append("a").append(n);
		a.andThen(n -> ints.append("b").append(n)).accept(1);
		System.out.println(ints);
		ToIntBiFunction<String, String> lengths = (x, y) -> x.length() + y.length();
		Supplier<String> supplier = () -> "supplied";
		System.out.println(lengths.applyAsInt("ab", "cde") + " " + supplier.get());
		try {
			longWord.and(null);
		} catch (NullPointerException e) {
			System.out.println("and(null): NullPointerException");
		}
	}
}
