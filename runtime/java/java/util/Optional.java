package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Optional, written from the Java SE specification. */
public final class Optional<T> {
	private static final Optional<?> EMPTY = new Optional<Object>(null);

	private final T value;

	private Optional(T value) {
		this.value = value;
	}

	@SuppressWarnings("unchecked")
	public static <T> Optional<T> empty() {
		return (Optional<T>) EMPTY;
	}

	public static <T> Optional<T> of(T value) {
		return new Optional<T>(Objects.requireNonNull(value));
	}

	public static <T> Optional<T> ofNullable(T value) {
		return value == null ? Optional.<T>empty() : new Optional<T>(value);
	}

	public T get() {
		if (value == null) {
			throw new NoSuchElementException("No value present");
		}
		return value;
	}

	public boolean isPresent() {
		return value != null;
	}

	public boolean isEmpty() {
		return value == null;
	}

	public void ifPresent(Consumer<? super T> action) {
		if (value != null) {
			action.accept(value);
		}
	}

	public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
		if (value != null) {
			action.accept(value);
		} else {
			emptyAction.run();
		}
	}

	public Optional<T> filter(Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate);
		if (value == null) {
			return this;
		}
		return predicate.test(value) ? this : Optional.<T>empty();
	}

	public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
		Objects.requireNonNull(mapper);
		if (value == null) {
			return empty();
		}
		return Optional.<U>ofNullable(mapper.apply(value));
	}

	@SuppressWarnings("unchecked")
	public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper) {
		Objects.requireNonNull(mapper);
		if (value == null) {
			return empty();
		}
		Optional<U> result = (Optional<U>) mapper.apply(value);
		return Objects.requireNonNull(result);
	}

	@SuppressWarnings("unchecked")
	public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
		Objects.requireNonNull(supplier);
		if (value != null) {
			return this;
		}
		Optional<T> result = (Optional<T>) supplier.get();
		return Objects.requireNonNull(result);
	}

	public T orElse(T other) {
		return value != null ? value : other;
	}

	public T orElseGet(Supplier<? extends T> supplier) {
		return value != null ? value : supplier.get();
	}

	public T orElseThrow() {
		if (value == null) {
			throw new NoSuchElementException("No value present");
		}
		return value;
	}

	public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (value != null) {
			return value;
		}
		throw exceptionSupplier.get();
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Optional)) {
			return false;
		}
		return Objects.equals(value, ((Optional<?>) obj).value);
	}

	public int hashCode() {
		return Objects.hashCode(value);
	}

	public String toString() {
		return value != null ? "Optional[" + value + "]" : "Optional.empty";
	}
}
