// Prints with the J2C runtime (runtime/); see the CI runtime step
public class Hello {
	public static void main(String[] args) {
		System.out.println("hi from translated Java");
		for (int i = 0; i < args.length; i++) {
			System.out.println(args[i]);
		}
		System.err.println("done");
	}
}
