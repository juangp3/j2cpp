package fixdemo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Quick fix fixture for J2C-LAMBDA-ANONYMOUS-THIS: every lambda and method
// reference below refers to the anonymous class around it. Used by
// vscode/scripts/test-cli.js, which expects the exact fix ranges.
public class AnonymousThis {
	interface Listener {
		void fire(int times);
	}

	final List<Object> seen = new ArrayList<Object>();

	Listener make() {
		return new Listener() {
			int fired;

			public void fire(int times) {
				Runnable remember = () -> seen.add(this);
				remember.run();
				if (times > 1)
					repeat(times, () -> this.fired++);
				Supplier<String> name = () -> toString() + fired;
				Consumer<Integer> each = this::count;
				Function<Integer, Integer> twice = (Integer x) -> {
					Supplier<Integer> inner = () -> this.fired + x;
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
