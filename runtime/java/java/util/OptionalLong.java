package java.util;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** OptionalLong, written from the Java SE specification. */
public final class OptionalLong {
	private static final OptionalLong EMPTY = new OptionalLong(false, 0);

	private final boolean isPresent;
	private final long value;

	private OptionalLong(boolean isPresent, long value) {
		this.isPresent = isPresent;
		this.value = value;
	}

	public static OptionalLong empty() {
		return EMPTY;
	}

	public static OptionalLong of(long value) {
		return new OptionalLong(true, value);
	}

	public long getAsLong() {
		if (!isPresent) {
			throw new NoSuchElementException("No value present");
		}
		return value;
	}

	public boolean isPresent() {
		return isPresent;
	}

	public boolean isEmpty() {
		return !isPresent;
	}

	public void ifPresent(LongConsumer action) {
		if (isPresent) {
			action.accept(value);
		}
	}

	public void ifPresentOrElse(LongConsumer action, Runnable emptyAction) {
		if (isPresent) {
			action.accept(value);
		} else {
			emptyAction.run();
		}
	}

	public long orElse(long other) {
		return isPresent ? value : other;
	}

	public long orElseGet(LongSupplier supplier) {
		return isPresent ? value : supplier.getAsLong();
	}

	public long orElseThrow() {
		if (!isPresent) {
			throw new NoSuchElementException("No value present");
		}
		return value;
	}

	public <X extends Throwable> long orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (isPresent) {
			return value;
		}
		throw exceptionSupplier.get();
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof OptionalLong)) {
			return false;
		}
		OptionalLong other = (OptionalLong) obj;
		return isPresent && other.isPresent ? value == other.value : isPresent == other.isPresent;
	}

	public int hashCode() {
		return isPresent ? Long.hashCode(value) : 0;
	}

	public String toString() {
		return isPresent ? "OptionalLong[" + value + "]" : "OptionalLong.empty";
	}
}
