package java.lang;

/** The base class of records; J2C generates their members */
public abstract class Record {
	protected Record() {
	}

	public abstract boolean equals(Object obj);

	public abstract int hashCode();

	public abstract String toString();
}
