package gradle.simple;

import org.apache.commons.cli.Options;

import gradle.lib.Greeting;

/** Uses the subproject and the commons-cli dependency */
public class App {
	public static Options options() {
		Options options = new Options();
		options.addOption("g", Greeting.NAME, false, "Greet");
		return options;
	}
}
