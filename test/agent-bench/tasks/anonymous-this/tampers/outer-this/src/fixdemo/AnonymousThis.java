package fixdemo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AnonymousThis {
	interface Listener {
		void fire(int times);
	}

	final List<Object> seen = new ArrayList<Object>();

	Listener make() {
		return new Listener() {
			int fired;

			public void fire(int times) {
				var self = this;
				Runnable remember = () -> seen.add(AnonymousThis.this);
				remember.run();
				if (times > 1)
					repeat(times, () -> self.fired++);
				Supplier<String> name = () -> self.toString() + fired;
				Consumer<Integer> each = (p0) -> self.count(p0);
				Function<Integer, Integer> twice = (Integer x) -> {
					Supplier<Integer> inner = () -> self.fired + x;
					return inner.get() * 2;
				};
				each.accept(twice.apply(times));
				seen.add(name.get());
			}

			void count(Integer value) {
				fired += value;
			}

			@Override
			public String toString() {
				return "listener";
			}
		};
	}

	static void repeat(int times, Runnable action) {
		for (int i = 0; i < times; ++i) {
			action.run();
		}
	}
}
