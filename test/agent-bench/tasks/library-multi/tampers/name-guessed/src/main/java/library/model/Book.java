package library.model;

public record Book(String title, String author, int year) implements Labeled {
	@Override
	public String label() {
		return title + " (" + year + ")";
	}
}
