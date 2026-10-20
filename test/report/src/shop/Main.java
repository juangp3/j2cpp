package shop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Migration report fixture: a file without problems. Used by
// vscode/scripts/test-cli.js, which expects the exact counts.
public class Main {
	public static void main(String[] args) {
		List<String> items = new ArrayList<String>();
		items.add("apple");
		items.add("pear");
		System.out.println(String.format("%d items", items.size()));
		System.out.println(LocalDate.now());
		System.out.println(Math.max(items.size(), 1));
	}

	static int total(List<Integer> prices) {
		int sum = 0;
		for (int i = 0; i < prices.size(); ++i) {
			sum += prices.get(i);
		}
		return sum;
	}
}
