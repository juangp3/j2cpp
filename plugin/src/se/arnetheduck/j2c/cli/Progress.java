package se.arnetheduck.j2c.cli;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * The progress and cancellation of one command, such as an MCP tool call.
 * Commands report phases (running the build tool) and counted items (files
 * checked or written), and stop with OperationCanceledException at the next
 * item or phase once cancelled. Progress values only increase. The command
 * line uses {@link #NONE}; the MCP server overrides {@link #report} to send
 * notifications.
 */
class Progress {
	/** Reports nothing and is never cancelled */
	static final Progress NONE = new Progress();

	private volatile boolean cancelled;
	/** Progress so far; every report increases it */
	private long count;
	/** The total of the items being counted, or -1 when unknown */
	private long total = -1;

	/**
	 * Receives each new progress value, with the total when known (else -1)
	 * and a message. Called on the thread that does the work. Frequent
	 * reports (items worked, compiler output) may be left out; phases and
	 * the start of a count are not.
	 */
	protected void report(long progress, long total, String message,
			boolean frequent) {
	}

	/** Asks the command to stop at its next item or phase */
	void cancel() {
		cancelled = true;
	}

	boolean cancelled() {
		return cancelled;
	}

	/** Throws OperationCanceledException once cancelled */
	void checkCancelled() {
		if (cancelled) {
			throw new OperationCanceledException();
		}
	}

	/** A step whose length is unknown, such as running the build tool */
	void phase(String message) {
		checkCancelled();
		long progress;
		synchronized (this) {
			total = -1;
			progress = ++count;
		}
		report(progress, -1, message, false);
	}

	/** One of many steps of the current phase, such as a compiled file */
	void step(String message) {
		checkCancelled();
		long progress;
		synchronized (this) {
			total = -1;
			progress = ++count;
		}
		report(progress, -1, message, true);
	}

	/** Starts counting items, the total when known, else -1 */
	void begin(long items, String message) {
		checkCancelled();
		long progress;
		long newTotal;
		synchronized (this) {
			progress = ++count;
			total = items < 0 ? -1 : count + items;
			newTotal = total;
		}
		report(progress, newTotal, message, false);
	}

	/** Finishes items of the count begun last */
	void worked(int items, String message) {
		checkCancelled();
		if (items <= 0) {
			return;
		}
		long progress;
		long currentTotal;
		synchronized (this) {
			count += items;
			if (total >= 0 && count > total) {
				total = count;
			}
			progress = count;
			currentTotal = total;
		}
		report(progress, currentTotal, message, true);
	}

	/**
	 * A monitor for the translator: beginTask starts a count, worked(n)
	 * finishes n items described by the latest subTask, and isCanceled
	 * reports cancellation
	 */
	IProgressMonitor monitor() {
		return new NullProgressMonitor() {
			private String message;

			@Override
			public void beginTask(String name, int totalWork) {
				message = name;
				begin(totalWork == IProgressMonitor.UNKNOWN ? -1 : totalWork,
						name);
			}

			@Override
			public void subTask(String name) {
				message = name;
			}

			@Override
			public void worked(int work) {
				Progress.this.worked(work, message);
			}

			@Override
			public boolean isCanceled() {
				return cancelled;
			}

			@Override
			public void setCanceled(boolean value) {
				if (value) {
					cancel();
				}
			}
		};
	}
}
