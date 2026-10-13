package java.lang.annotation;

/** The interface every annotation type extends */
public interface Annotation {
	boolean equals(Object obj);

	int hashCode();

	String toString();

	Class<? extends Annotation> annotationType();
}
