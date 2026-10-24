package fixdemo;

import java.util.function.Function;
import java.util.function.Supplier;

public class InterfaceSuper implements Named {
	@Override
	public String describe(String prefix) {
		Supplier<String> base = () -> Named.super.describe(prefix + ">");
		return base.get() + "!";
	}

	Function<String, String> describer() {
		return Named.super::describe;
	}
}

interface Named {
	default String describe(String prefix) {
		return prefix + "named";
	}
}
