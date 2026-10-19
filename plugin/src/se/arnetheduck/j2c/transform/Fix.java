package se.arnetheduck.j2c.transform;

import java.util.Collections;
import java.util.List;

/**
 * Edits to the Java source that remove a problem, for problems whose fix is
 * mechanical. Applying every edit of a fix gives Java that compiles and that
 * J2C translates without that problem.
 */
public final class Fix {
	public final String title;
	public final List<SourceEdit> edits;

	public Fix(String title, List<SourceEdit> edits) {
		this.title = title;
		this.edits = Collections.unmodifiableList(edits);
	}
}
