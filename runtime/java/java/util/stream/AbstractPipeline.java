package java.util.stream;

import java.util.ArrayList;
import java.util.Objects;

/**
 * The part of a stream stage the four pipeline classes share: whether the
 * stage was used, and the close handlers of its pipeline.
 *
 * The runtime's streams are pull-based and sequential: each stage is a source
 * whose tryAdvance pulls one element through the stages above it, so elements
 * go through the pipeline one at a time in the JVM's order, and short-circuit
 * operations stop pulling, which ends traversal of infinite streams.
 */
abstract class AbstractPipeline {
	static final String CONSUMED = "stream has already been operated upon or closed";

	/** What the stages of one pipeline share */
	static final class Shared {
		ArrayList<Runnable> closeHandlers;
		boolean closed;
		boolean parallel;
	}

	final Shared shared;
	private boolean linked;

	AbstractPipeline(Shared shared) {
		this.shared = shared;
	}

	/** Marks this stage used by an operation; a stage can be used once */
	final void link() {
		if (linked || shared.closed) {
			throw new IllegalStateException(CONSUMED);
		}
		linked = true;
	}

	final void addCloseHandler(Runnable closeHandler) {
		Objects.requireNonNull(closeHandler);
		if (linked || shared.closed) {
			throw new IllegalStateException(CONSUMED);
		}
		if (shared.closeHandlers == null) {
			shared.closeHandlers = new ArrayList<Runnable>();
		}
		shared.closeHandlers.add(closeHandler);
	}

	/**
	 * Runs the close handlers once, in order. Every handler runs; the first
	 * exception is thrown, with later ones added to it as suppressed.
	 */
	final void closePipeline() {
		if (shared.closed) {
			return;
		}
		shared.closed = true;
		linked = true;
		ArrayList<Runnable> handlers = shared.closeHandlers;
		shared.closeHandlers = null;
		if (handlers != null) {
			runAll(handlers);
		}
	}

	static void runAll(ArrayList<Runnable> handlers) {
		RuntimeException failure = null;
		for (int i = 0; i < handlers.size(); i++) {
			try {
				handlers.get(i).run();
			} catch (RuntimeException e) {
				if (failure == null) {
					failure = e;
				} else if (failure != e) {
					failure.addSuppressed(e);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	/** Closes two streams, as the stream concat returns does */
	static final class CloseBoth implements Runnable {
		private final BaseStream<?, ?> first;
		private final BaseStream<?, ?> second;

		CloseBoth(BaseStream<?, ?> first, BaseStream<?, ?> second) {
			this.first = first;
			this.second = second;
		}

		public void run() {
			ArrayList<Runnable> handlers = new ArrayList<Runnable>();
			handlers.add(new CloseOne(first));
			handlers.add(new CloseOne(second));
			runAll(handlers);
		}
	}

	static final class CloseOne implements Runnable {
		private final BaseStream<?, ?> stream;

		CloseOne(BaseStream<?, ?> stream) {
			this.stream = stream;
		}

		public void run() {
			stream.close();
		}
	}

	/** A size that may be unknown (negative) after adding two sizes */
	static long addSizes(long a, long b) {
		if (a < 0 || b < 0) {
			return -1;
		}
		long sum = a + b;
		return sum < 0 ? -1 : sum;
	}

	static long limitSize(long size, long maxSize) {
		return size < 0 ? -1 : Math.min(size, maxSize);
	}

	static long skipSize(long size, long n) {
		return size < 0 ? -1 : Math.max(0, size - n);
	}

	/** The compensated sum, or the plain sum when compensation gives NaN for an infinite sum */
	static double finalSum(double sum, double compensation, double simpleSum) {
		double tmp = sum - compensation;
		if (Double.isNaN(tmp) && Double.isInfinite(simpleSum)) {
			return simpleSum;
		}
		return tmp;
	}
}
