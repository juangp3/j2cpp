package library;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import library.model.Book;
import library.model.Shelf;
import library.service.Catalog;
import library.util.Orders;

public class BenchDriver {
	public static void main(String[] args) {
		Shelf shelf = new Shelf()
				.add(new Book("Dune", "Herbert", 1965))
				.add(new Book("Emma", "Austen", 1815))
				.add(new Book("Ubik", "Dick", 1969))
				.add(new Book("Persuasion", "Austen", 1817));
		for (Book book : shelf) {
			System.out.println(book.label() + " / " + book.shortLabel(8));
		}
		Iterator<Book> austen = shelf.matching(book -> book.author().equals("Austen"));
		while (austen.hasNext()) {
			System.out.println("Austen: " + austen.next().title());
		}
		System.out.println(shelf.matching(book -> false).hasNext());
		Catalog catalog = new Catalog(shelf);
		System.out.println(catalog.label());
		System.out.println(catalog.shortLabel(12));
		System.out.println(catalog.titlesSince(1900));
		System.out.println(catalog.summary());
		List<Book> sorted = new ArrayList<Book>();
		shelf.forEach(sorted::add);
		Comparator<Book> byYear = Orders.by(Book::year);
		sorted.sort(byYear);
		System.out.println(sorted.get(0).title() + " .. " + sorted.get(3).title());
		sorted.sort(Orders.by(Book::title));
		System.out.println(sorted.get(0).title() + " .. " + sorted.get(3).title());
		System.out.println(byYear instanceof Serializable);
	}
}
