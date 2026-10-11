package se.arnetheduck.j2c.snippets;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import se.arnetheduck.j2c.transform.CName;
import se.arnetheduck.j2c.transform.EmptySnippet;
import se.arnetheduck.j2c.transform.StubWriter;
import se.arnetheduck.j2c.transform.TransformUtil;
import se.arnetheduck.j2c.transform.Transformer;

public class GetSetSnippet extends EmptySnippet {

	@Override
	public boolean body(Transformer ctx, StubWriter w, IMethodBinding mb) {
		String name = mb.getName();
		if (name.length() < 4)
			return true;

		boolean getter = name.startsWith("get");
		boolean setter = name.startsWith("set");
		if (!getter && !setter)
			return true;

		char[] vname = name.substring(3).toCharArray();
		vname[0] = Character.toLowerCase(vname[0]);
		ITypeBinding tb = mb.getDeclaringClass();
		if (tb == null)
			return true;

		String v = new String(vname);
		boolean ms = TransformUtil.isStatic(mb);

		for (IVariableBinding vb : tb.getDeclaredFields()) {
			if (ms == TransformUtil.isStatic(vb) && vb.getName().equals(v)) {
				boolean asMethod = TransformUtil.asMethod(vb);
				// a.isAssignmentCompatible(b): a value of type a fits in b
				if (getter
						&& vb.getType().getErasure().isAssignmentCompatible(
								mb.getReturnType().getErasure())) {
					// Converting to a wider return type needs the field's class
					w.hardDep(vb.getType());
					w.println("return " + CName.of(vb) + (asMethod ? "()" : "")
							+ " ; /* getter */");
					return false;
				} else if (setter
						&& mb.getReturnType().getName().equals("void")
						&& mb.getParameterTypes().length == 1
						&& mb.getParameterTypes()[0].getErasure()
								.isAssignmentCompatible(vb.getType().getErasure())) {
					w.print(TransformUtil.indent(1));
					if (ms) {
						w.print(CName.of(tb) + "::");
					} else {
						w.print("this->");
					}

					w.println(CName.of(vb) + (asMethod ? "()" : "") + " = "
							+ ctx.paramName(mb, 0) + "; /* setter */");
					return false;
				}

				// Types differ, as in setType(Object) beside a Class field
				return true;
			}
		}

		return true;
	}
}
