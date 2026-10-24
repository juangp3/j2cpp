package fixdemo;

import java.util.function.Function;
import java.util.function.Supplier;

public class InterfaceSuper implements Named {
	@Override
	public String describe(String prefix) {
		Supplier<String> base = () -> describeFromNamed(prefix + ">");
		return base.get() + "!";
	}

	Function<String, String> describer() {
		return this::describeFromNamed;
	}

	private String describeFromNamed(String p0) {
		return Named.super.describe(p0);
	}
}

interface Named {
	default String describe(String prefix) {
		return prefix + "named";
	}
}
