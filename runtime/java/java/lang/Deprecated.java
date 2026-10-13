package java.lang;

public @interface Deprecated {
	String since() default "";

	boolean forRemoval() default false;
}
