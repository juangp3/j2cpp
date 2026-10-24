package counters;

public class Counters {
	public interface Counter {
		int next();

		Runnable resetter();
	}

	public static Counter startingAt(int start) {
		class StartCounter implements Counter {
			int value = start;
			final Runnable reset = new Runnable() {
				@Override
				public void run() {
					value = start;
				}
			};

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
		}
		return new StartCounter();
	}
}
