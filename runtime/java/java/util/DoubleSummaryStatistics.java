package java.util;

import java.util.function.DoubleConsumer;

/**
 * DoubleSummaryStatistics, written from the Java SE specification. The sum
 * uses Kahan (compensated) summation, as the JDK's does, so results match.
 */
public class DoubleSummaryStatistics implements DoubleConsumer {
	private long count;
	private double sum;
	/** The running compensation: the low-order bits lost from sum */
	private double compensation;
	/** The plain sum, used when compensation turns an infinite sum into NaN */
	private double simpleSum;
	private double min = Double.POSITIVE_INFINITY;
	private double max = Double.NEGATIVE_INFINITY;

	public DoubleSummaryStatistics() {
	}

	public DoubleSummaryStatistics(long count, double min, double max, double sum) throws IllegalArgumentException {
		if (count < 0L) {
			throw new IllegalArgumentException("Negative count value");
		}
		if (count > 0L) {
			if (min > max) {
				throw new IllegalArgumentException("Minimum greater than maximum");
			}
			long ncount = 0;
			if (Double.isNaN(min)) {
				ncount++;
			}
			if (Double.isNaN(max)) {
				ncount++;
			}
			if (Double.isNaN(sum)) {
				ncount++;
			}
			if (ncount > 0 && ncount < 3) {
				throw new IllegalArgumentException("Some, not all, of the minimum, maximum, or sum is NaN");
			}
			this.count = count;
			this.sum = sum;
			this.simpleSum = sum;
			this.min = min;
			this.max = max;
		}
	}

	public void accept(double value) {
		count++;
		simpleSum += value;
		add(value);
		min = Math.min(min, value);
		max = Math.max(max, value);
	}

	public void combine(DoubleSummaryStatistics other) {
		count += other.count;
		simpleSum += other.simpleSum;
		add(other.sum);
		add(-other.compensation);
		min = Math.min(min, other.min);
		max = Math.max(max, other.max);
	}

	/** One step of Kahan summation */
	private void add(double value) {
		double y = value - compensation;
		double t = sum + y;
		compensation = (t - sum) - y;
		sum = t;
	}

	public final long getCount() {
		return count;
	}

	public final double getSum() {
		return finalSum(sum, compensation, simpleSum);
	}

	/** The compensated sum, or the plain sum when compensation gives NaN for an infinite sum */
	static double finalSum(double sum, double compensation, double simpleSum) {
		double tmp = sum - compensation;
		if (Double.isNaN(tmp) && Double.isInfinite(simpleSum)) {
			return simpleSum;
		}
		return tmp;
	}

	public final double getMin() {
		return min;
	}

	public final double getMax() {
		return max;
	}

	public final double getAverage() {
		return getCount() > 0 ? getSum() / getCount() : 0.0d;
	}

	public String toString() {
		return String.format("%s{count=%d, sum=%f, min=%f, average=%f, max=%f}", getClass().getSimpleName(),
				getCount(), getSum(), getMin(), getAverage(), getMax());
	}
}
