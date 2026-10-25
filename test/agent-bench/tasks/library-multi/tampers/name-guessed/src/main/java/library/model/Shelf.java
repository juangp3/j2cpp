package library.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class Shelf implements Iterable<Book> {
	private final List<Book> books = new ArrayList<Book>();

	public Shelf add(Book book) {
		books.add(book);
		return this;
	}

	public int size() {
		return books.size();
	}

	/** Books in shelf order, skipping those the filter rejects */
	public Iterator<Book> matching(Predicate<Book> filter) {
		return new Iterator<Book>() {
			int next = advance(0);
			final Runnable skip = new Runnable() {
				@Override
				public void run() {
					next = advance(next + 1);
				}
			};

			int advance(int from) {
				while (from < books.size() && !filter.test(books.get(from))) {
					from++;
				}
				return from;
			}

			@Override
			public boolean hasNext() {
				return next < books.size();
			}

			@Override
			public Book next() {
				Book book = books.get(next);
				skip.run();
				return book;
			}
		};
	}

	@Override
	public Iterator<Book> iterator() {
		return matching(book -> true);
	}
}
