package counters;

public class Counters {
	public interface Counter {
		int next();

		Runnable resetter();
	}

	public static Counter startingAt(int start) {
		return new Counter() {
			int value = start;
			final Runnable reset = () -> this.value = start;

			@Override
			public int next() {
				return value++;
			}

			@Override
			public Runnable resetter() {
				return reset;
			}

			@Override
			public String toString() {
				return "counter at " + value;
			}
		};
	}
}
