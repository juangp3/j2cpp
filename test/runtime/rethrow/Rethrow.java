import java.io.IOException;

// G45 and G46: rethrowing keeps the dynamic type, and try-with-resources
// closes resources and records suppressed exceptions as JLS 14.20.3 says
public class Rethrow {
	static class Resource implements AutoCloseable {
		private final String name;
		private final boolean fail;

		Resource(String name, boolean fail) {
			this.name = name;
			this.fail = fail;
			System.out.println("open " + name);
		}

		public void close() throws IOException {
			System.out.println("close " + name);
			if (fail) {
				throw new IOException("close " + name);
			}
		}
	}

	static void rethrow(Throwable t) throws Throwable {
		try {
			throw t;
		} catch (Throwable caught) {
			throw caught;
		}
	}

	static Exception make(String message) {
		return new IllegalStateException(message);
	}

	static void dynamicType() {
		try {
			rethrow(new IOException("io"));
		} catch (IOException e) {
			System.out.println("caught IOException " + e.getMessage());
		} catch (Throwable t) {
			System.out.println("wrong handler " + t);
		}
		try {
			throw make("made");
		} catch (IllegalStateException e) {
			System.out.println("caught IllegalStateException " + e.getMessage());
		} catch (Exception e) {
			System.out.println("wrong handler " + e);
		}
		try {
			RuntimeException none = null;
			throw none;
		} catch (NullPointerException e) {
			System.out.println("throw null raises NullPointerException");
		}
	}

	static void print(Throwable t) {
		System.out.println("caught " + t);
		Throwable[] suppressed = t.getSuppressed();
		for (int i = 0; i < suppressed.length; i++) {
			System.out.println("  suppressed " + suppressed[i]);
		}
	}

	static int early() throws IOException {
		try (Resource r = new Resource("early", false)) {
			return 42;
		}
	}

	static void resources() {
		// close() throwing on a normal exit
		try (Resource r = new Resource("a", true)) {
			System.out.println("body a");
		} catch (IOException e) {
			print(e);
		}
		// The body's exception wins, and close() exceptions are suppressed
		try (Resource r = new Resource("b", true); Resource s = new Resource("c", true)) {
			throw new IllegalArgumentException("body");
		} catch (Exception e) {
			print(e);
		}
		// The inner close() exception wins over the outer one
		try (Resource r = new Resource("d", true); Resource s = new Resource("e", true)) {
			System.out.println("body d e");
		} catch (IOException e) {
			print(e);
		}
		// A null resource is not closed
		try (Resource r = null) {
			System.out.println("null resource");
		} catch (IOException e) {
			print(e);
		}
		try {
			System.out.println("early " + early());
		} catch (IOException e) {
			print(e);
		}
		// An existing variable as a resource
		Resource f = new Resource("f", false);
		try (f) {
			System.out.println("body f");
		} catch (IOException e) {
			print(e);
		} finally {
			System.out.println("finally f");
		}
	}

	static void throwingFinally() {
		try {
			try {
				System.out.println("try");
			} finally {
				throw new IllegalStateException("from finally");
			}
		} catch (IllegalStateException e) {
			print(e);
		}
	}

	public static void main(String[] args) throws Exception {
		dynamicType();
		resources();
		throwingFinally();
		try (Resource r = new Resource("uncaught", true)) {
			throw new UnsupportedOperationException("last");
		}
	}
}
