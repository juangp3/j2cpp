package sizes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class BenchDriver {
	public static void main(String[] args) {
		List<Collection<Integer>> groups = new ArrayList<Collection<Integer>>();
		groups.add(List.of(1, 2));
		groups.add(Set.of(3));
		groups.add(new ArrayList<Integer>());
		System.out.println(Sizes.totalSize(groups));
		System.out.println(Sizes.totalSize(new ArrayList<List<String>>()));
		System.out.println(Sizes.sum(List.of("ab", "cde"), String::length));
	}
}
