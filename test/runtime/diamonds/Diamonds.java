import java.util.function.Consumer;

// G35: interfaces that specialize a generic interface's method for a
// primitive type, as Spliterator.OfInt does, implemented by a generic
// superclass, as StreamSpliterators.SliceSpliterator.OfPrimitive does
public class Diamonds {
	interface IntAction {
		void accept(int value);
	}

	interface Source<T, C> {
		boolean advance(C action);

		default void drain(C action) {
			while (advance(action)) {
			}
		}
	}

	interface Boxed<T> {
		boolean advance(Consumer<? super T> action);
	}

	interface IntSource extends Source<Integer, IntAction>, Boxed<Integer> {
		@Override
		boolean advance(IntAction action);

		@Override
		default boolean advance(Consumer<? super Integer> action) {
			return advance((IntAction) action::accept);
		}
	}

	static class Range implements IntSource {
		private int next;
		private final int end;

		Range(int start, int end) {
			this.next = start;
			this.end = end;
		}

		@Override
		public boolean advance(IntAction action) {
			if (next >= end) {
				return false;
			}
			action.accept(next++);
			return true;
		}
	}

	// The generic superclass implements advance(C), which erases to
	// advance(Object); IntSource.advance(IntAction) needs a bridge in OfInt
	abstract static class Counter<T, C> implements Source<T, C> {
		int left;

		Counter(int left) {
			this.left = left;
		}

		abstract void emit(C action, int value);

		@Override
		public boolean advance(C action) {
			if (left == 0) {
				return false;
			}
			emit(action, left--);
			return true;
		}
	}

	static final class OfInt extends Counter<Integer, IntAction> implements
			IntSource {
		OfInt(int left) {
			super(left);
		}

		@Override
		void emit(IntAction action, int value) {
			action.accept(value * 10);
		}
	}

	// A subclass of a generic class that also implements the bridge's
	// interface: the bridge is declared once, in OfInt
	static class Wider extends Counter<Integer, IntAction> implements
			Source<Integer, IntAction> {
		Wider(int left) {
			super(left);
		}

		@Override
		void emit(IntAction action, int value) {
			action.accept(-value);
		}
	}

	// A class method beats a default with another erasure, as
	// Nodes.ConcNode.OfInt.copyInto does over Node.OfInt.copyInto
	interface Node<T> {
		String copyInto(T[] array);

		String forEach(Consumer<? super T> action);
	}

	interface IntNode extends Node<Integer> {
		@Override
		default String copyInto(Integer[] boxed) {
			return "IntNode.copyInto";
		}
	}

	static class Conc<T> implements Node<T> {
		@Override
		public String copyInto(T[] array) {
			return "Conc.copyInto " + array.length;
		}

		@Override
		public String forEach(Consumer<? super T> action) {
			return "Conc.forEach";
		}
	}

	static final class IntConc extends Conc<Integer> implements IntNode {
	}

	// A generic superclass's methods implement the interface's once its
	// type arguments are known, as in Nodes.EmptyNode.OfRef
	abstract static class Empty<T, A, C> implements Node<T> {
		public String copyInto(A array) {
			return "Empty.copyInto";
		}

		public String forEach(C action) {
			return "Empty.forEach";
		}
	}

	static final class EmptyRef<T> extends Empty<T, T[], Consumer<? super T>> {
	}

	// The same default reached through two paths
	interface Named {
		default String name() {
			return "named";
		}
	}

	interface Left extends Named {
	}

	interface Right extends Named {
	}

	static class Both implements Left, Right {
	}

	static IntAction print(String label) {
		return new IntAction() {
			@Override
			public void accept(int value) {
				System.out.println(label + " " + value);
			}
		};
	}

	public static void main(String[] args) {
		new Range(0, 3).drain(print("int"));
		Source<Integer, IntAction> source = new Range(5, 7);
		source.drain(print("source"));
		Boxed<Integer> boxed = new Range(10, 12);
		while (boxed.advance(new Consumer<Integer>() {
			@Override
			public void accept(Integer value) {
				System.out.println("boxed " + value);
			}
		})) {
		}

		OfInt ofInt = new OfInt(2);
		ofInt.advance(print("ofInt"));
		IntSource intSource = new OfInt(2);
		intSource.drain(print("intSource"));
		Source<Integer, IntAction> counted = new OfInt(1);
		counted.advance(print("counted"));
		Boxed<Integer> boxedCount = new OfInt(1);
		boxedCount.advance(new Consumer<Integer>() {
			@Override
			public void accept(Integer value) {
				System.out.println("boxedCount " + value);
			}
		});
		new Wider(2).drain(print("wider"));

		Integer[] boxes = new Integer[2];
		IntConc intConc = new IntConc();
		System.out.println(intConc.copyInto(boxes));
		IntNode intNode = intConc;
		System.out.println(intNode.copyInto(boxes));
		Node<Integer> node = intConc;
		System.out.println(node.copyInto(boxes));
		System.out.println(node.forEach(null));

		Node<String> emptyRef = new EmptyRef<String>();
		System.out.println(emptyRef.copyInto(new String[0]));
		System.out.println(emptyRef.forEach(null));
		Empty<String, String[], Consumer<? super String>> empty = new EmptyRef<String>();
		System.out.println(empty.copyInto(new String[0]));
		System.out.println(new Both().name());
	}
}
