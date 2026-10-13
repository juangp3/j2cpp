package java.util;

import java.util.function.IntConsumer;

/** IntSummaryStatistics, written from the Java SE specification. */
public class IntSummaryStatistics implements IntConsumer {
	private long count;
	private long sum;
	private int min = Integer.MAX_VALUE;
	private int max = Integer.MIN_VALUE;

	public IntSummaryStatistics() {
	}

	public IntSummaryStatistics(long count, int min, int max, long sum) throws IllegalArgumentException {
		if (count < 0L) {
			throw new IllegalArgumentException("Negative count value");
		}
		if (count > 0L) {
			if (min > max) {
				throw new IllegalArgumentException("Minimum greater than maximum");
			}
			this.count = count;
			this.sum = sum;
			this.min = min;
			this.max = max;
		}
	}

	public void accept(int value) {
		count++;
		sum += value;
		min = Math.min(min, value);
		max = Math.max(max, value);
	}

	public void combine(IntSummaryStatistics other) {
		count += other.count;
		sum += other.sum;
		min = Math.min(min, other.min);
		max = Math.max(max, other.max);
	}

	public final long getCount() {
		return count;
	}

	public final long getSum() {
		return sum;
	}

	public final int getMin() {
		return min;
	}

	public final int getMax() {
		return max;
	}

	public final double getAverage() {
		return count > 0 ? (double) sum / count : 0.0d;
	}

	public String toString() {
		return String.format("%s{count=%d, sum=%d, min=%d, average=%f, max=%d}", getClass().getSimpleName(),
				getCount(), getSum(), getMin(), getAverage(), getMax());
	}
}
