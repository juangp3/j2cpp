package simple;

import org.apache.commons.cli.Options;

/** Uses a type from the commons-cli dependency, so it needs the class path */
public class App {
	public static Options options() {
		Options options = new Options();
		options.addOption("v", "verbose", false, "Print more");
		return options;
	}

	public static int optionCount() {
		return options().getOptions().size();
	}
}
