package library.model;

public interface Labeled {
	String label();

	default String shortLabel(int width) {
		String label = label();
		return label.length() <= width ? label : label.substring(0, width - 1) + "~";
	}
}
