package se.arnetheduck.j2c.transform;

import java.lang.reflect.Modifier;
import java.util.List;

import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

final class RecordSupport {
	private RecordSupport() {
	}

	static boolean hasMethod(RecordDeclaration record, String name,
			int parameterCount) {
		for (Object declaration : record.bodyDeclarations()) {
			if (declaration instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) declaration;
				IMethodBinding binding = method.resolveBinding();
				if (binding != null && !method.isConstructor()
						&& binding.getName().equals(name)
						&& binding.getParameterTypes().length == parameterCount) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean hasCanonicalConstructor(RecordDeclaration record) {
		List<SingleVariableDeclaration> components = record.recordComponents();
		for (Object declaration : record.bodyDeclarations()) {
			if (!(declaration instanceof MethodDeclaration)) {
				continue;
			}

			MethodDeclaration method = (MethodDeclaration) declaration;
			if (!method.isConstructor()) {
				continue;
			}
			if (method.isCompactConstructor()) {
				return true;
			}
			if (method.parameters().size() != components.size()) {
				continue;
			}

			boolean matches = true;
			for (int i = 0; i < components.size(); i++) {
				SingleVariableDeclaration component = components.get(i);
				SingleVariableDeclaration parameter = (SingleVariableDeclaration) method
						.parameters().get(i);
				if (!component.getName().getIdentifier()
						.equals(parameter.getName().getIdentifier())
						|| !component.resolveBinding().getType().isEqualTo(
								parameter.resolveBinding().getType())) {
					matches = false;
					break;
				}
			}
			if (matches) {
				return true;
			}
		}
		return false;
	}

	static boolean hasAccessor(RecordDeclaration record,
			SingleVariableDeclaration component) {
		return hasMethod(record, component.getName().getIdentifier(), 0);
	}

	static boolean hasEquals(RecordDeclaration record) {
		for (Object declaration : record.bodyDeclarations()) {
			if (declaration instanceof MethodDeclaration) {
				IMethodBinding binding = ((MethodDeclaration) declaration)
						.resolveBinding();
				if (binding != null && !binding.isConstructor()
						&& binding.getName().equals("equals")
						&& binding.getParameterTypes().length == 1
						&& binding.getParameterTypes()[0].getErasure()
								.getQualifiedName().equals(
										"java.lang.Object")) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Does the implicit accessor of a component implement a method of a
	 * supertype? It must then be a virtual override rather than a const
	 * member, or the record stays abstract in C++ (KeywordView.title())
	 */
	static boolean accessorOverrides(ITypeBinding type,
			SingleVariableDeclaration component) {
		String name = component.getName().getIdentifier();
		for (IMethodBinding mb : TypeUtil.methods(TypeUtil.allBases(type,
				null))) {
			int modifiers = mb.getModifiers();
			if (mb.getName().equals(name)
					&& mb.getParameterTypes().length == 0
					&& !Modifier.isStatic(modifiers)
					&& !Modifier.isPrivate(modifiers)) {
				return true;
			}
		}
		return false;
	}

	static boolean isComponent(IVariableBinding variable) {
		return variable != null && variable.isRecordComponent();
	}
}
