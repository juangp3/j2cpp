package java.util.concurrent;

/** TimeUnit, written from the Java SE specification; conversions saturate */
public enum TimeUnit {
	NANOSECONDS(1L),
	MICROSECONDS(1000L),
	MILLISECONDS(1000000L),
	SECONDS(1000000000L),
	MINUTES(60000000000L),
	HOURS(3600000000000L),
	DAYS(86400000000000L);

	/** Nanoseconds per unit */
	private final long scale;

	TimeUnit(long scale) {
		this.scale = scale;
	}

	/** duration in units of sourceScale nanoseconds, in units of targetScale */
	private static long convert(long duration, long targetScale, long sourceScale) {
		if (sourceScale == targetScale) {
			return duration;
		}
		if (sourceScale < targetScale) {
			return duration / (targetScale / sourceScale);
		}
		long ratio = sourceScale / targetScale;
		long max = Long.MAX_VALUE / ratio;
		if (duration > max) {
			return Long.MAX_VALUE;
		}
		if (duration < -max) {
			return Long.MIN_VALUE;
		}
		return duration * ratio;
	}

	public long convert(long sourceDuration, TimeUnit sourceUnit) {
		return convert(sourceDuration, scale, sourceUnit.scale);
	}

	public long toNanos(long duration) {
		return convert(duration, 1L, scale);
	}

	public long toMicros(long duration) {
		return convert(duration, 1000L, scale);
	}

	public long toMillis(long duration) {
		return convert(duration, 1000000L, scale);
	}

	public long toSeconds(long duration) {
		return convert(duration, 1000000000L, scale);
	}

	public long toMinutes(long duration) {
		return convert(duration, 60000000000L, scale);
	}

	public long toHours(long duration) {
		return convert(duration, 3600000000000L, scale);
	}

	public long toDays(long duration) {
		return convert(duration, 86400000000000L, scale);
	}

	/** Milliseconds, rounded up, for Object.wait */
	static long waitMillis(long nanos) {
		if (nanos <= 0) {
			return 0;
		}
		long millis = nanos / 1000000L;
		return nanos % 1000000L == 0 ? millis : millis + 1;
	}

	public void timedWait(Object obj, long timeout) throws InterruptedException {
		if (timeout > 0) {
			obj.wait(waitMillis(toNanos(timeout)));
		}
	}

	public void timedJoin(Thread thread, long timeout) throws InterruptedException {
		if (timeout > 0) {
			thread.join(waitMillis(toNanos(timeout)));
		}
	}

	public void sleep(long timeout) throws InterruptedException {
		if (timeout > 0) {
			Thread.sleep(waitMillis(toNanos(timeout)));
		}
	}
}
