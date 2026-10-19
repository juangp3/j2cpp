package multi.core;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import multi.extra.Names;

/** Uses commons-cli and a class from the build-helper source root */
public class Flags {
	public static Options all() {
		Options options = new Options();
		options.addOption(new Option("q", Names.QUIET, false, "Print less"));
		return options;
	}
}
