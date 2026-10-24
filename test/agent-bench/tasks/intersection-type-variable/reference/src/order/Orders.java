package order;

import java.io.Serializable;
import java.util.Comparator;

public class Orders {
	private static final class Natural<T extends Comparable<T>> implements Comparator<T>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(T a, T b) {
			return a.compareTo(b);
		}
	}

	private static final class Reversed<T extends Comparable<T>> implements Comparator<T>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(T a, T b) {
			return b.compareTo(a);
		}
	}

	public static <T extends Comparable<T>> Comparator<T> natural() {
		return new Natural<T>();
	}

	public static <T extends Comparable<T>> Comparator<T> reversed() {
		return new Reversed<T>();
	}
}
