// expect: compiles
package probes;
public class Sealed {
	sealed interface Expr permits Num, Add {}
	record Num(int v) implements Expr {}
	record Add(Expr l, Expr r) implements Expr {}
	static int eval(Expr e) {
		if (e instanceof Num n) return n.v();
		if (e instanceof Add a) return eval(a.l()) + eval(a.r());
		throw new IllegalStateException();
	}
}
