package library.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import library.model.Book;
import library.model.Labeled;
import library.model.Shelf;

public class Catalog implements Labeled {
	static final Object DEFAULT_NAME = "Main catalog";
	static final String NAME = "Catalog";

	private static String initNAME() {
		return DEFAULT_NAME instanceof String s && !s.isBlank() ? s : "Catalog";
	}

	private final Shelf shelf;

	public Catalog(Shelf shelf) {
		this.shelf = shelf;
	}

	@Override
	public String label() {
		return NAME + ": " + shelf.size() + " books";
	}

	@Override
	public String shortLabel(int width) {
		Function<Integer, String> base = w -> shortLabelFromLabeled(w);
		return "[" + base.apply(width) + "]";
	}

	public List<String> titlesSince(int year) {
		List<String> titles = new ArrayList<String>();
		for (Iterator<Book> books = shelf.matching(book -> book.year() >= year); books.hasNext();) {
			titles.add(books.next().title());
		}
		return titles;
	}

	public String summary() {
		return label() + ", " + titlesSince(1900).size() + " since 1900, " + shelf.size() + " shelved";
	}

	private String shortLabelFromLabeled(int p0) {
		return Labeled.super.shortLabel(p0);
	}
}
