package fixdemo;

public class BenchDriver {
	public static void main(String[] args) {
		AnonymousThis owner = new AnonymousThis();
		AnonymousThis.Listener listener = owner.make();
		listener.fire(1);
		listener.fire(3);
		for (Object seen : owner.seen) {
			System.out.println(seen == listener ? "the listener" : seen == owner ? "the owner" : String.valueOf(seen));
		}
	}
}
