package counters;

public class BenchDriver {
	public static void main(String[] args) {
		Counters.Counter counter = Counters.startingAt(5);
		Counters.Counter other = Counters.startingAt(1);
		System.out.println(counter.next() + " " + counter.next() + " " + counter.next());
		System.out.println(counter.resetter() == counter.resetter());
		other.next();
		counter.resetter().run();
		System.out.println(counter.next() + " " + other.next());
		System.out.println(counter + ", " + other);
	}
}
