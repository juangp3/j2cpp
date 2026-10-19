package java.util.regex;

/** MatchResult, written from the Java SE specification. */
public interface MatchResult {
	int start();

	int start(int group);

	int end();

	int end(int group);

	String group();

	String group(int group);

	int groupCount();
}
