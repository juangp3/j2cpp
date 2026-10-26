package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class TypeInfoVisitor extends ASTVisitor {
	private UnitInfo unitInfo;
	private TypeInfo typeInfo;
	private final List<Capture> captures = new ArrayList<Capture>();

	public TypeInfoVisitor(UnitInfo unitInfo) {
		this.unitInfo = unitInfo;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		captureSuperclass(node);
		return true;
	}

	/**
	 * A class extending a local class passes the variables that class
	 * captured to its constructor, so it captures them too
	 */
	private void captureSuperclass(ASTNode node) {
		ITypeBinding superclass = typeInfo.type().getSuperclass();
		if (superclass != null && superclass.isLocal()) {
			capture(new Capture(typeInfo, superclass.getErasure(), node));
		}
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		TypeInfo anonType = typeInfo;
		typeInfo = typeInfo.parent();

		// The enclosing class creates the anonymous class, so it must have
		// the variables to pass
		capture(new Capture(typeInfo, anonType.type(),
				enclosingTypeBody(node.getParent())));
	}

	/** Applies a capture now, which keeps closures in source order, and again later */
	private void capture(Capture capture) {
		capture.apply();
		captures.add(capture);
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		// A local class created from another class (an anonymous class, a
		// lambda's class, another local class) gets its captured variables
		// from that class, which must capture them too, as javac does
		ITypeBinding created = node.getType().resolveBinding();
		if (node.getAnonymousClassDeclaration() == null && created != null
				&& created.isLocal()) {
			capture(new Capture(typeInfo, created.getErasure(),
					enclosingTypeBody(node)));
		}
		return true;
	}

	@Override
	public void endVisit(CompilationUnit node) {
		// Captures chain (a class creating a class creating a local class),
		// so repeat until nothing changes
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Capture capture : captures) {
				changed |= capture.apply();
			}
		}
	}

	/** A class (creator) that creates a local class and passes its closures */
	private final class Capture {
		private final TypeInfo creator;
		private final ITypeBinding created;
		/** The class body the creation is in */
		private final ASTNode scope;

		Capture(TypeInfo creator, ITypeBinding created, ASTNode scope) {
			this.creator = creator;
			this.created = created;
			this.scope = scope;
		}

		boolean apply() {
			TypeInfo createdInfo = unitInfo.types.get(created);
			if (creator.closures() == null || createdInfo == null
					|| createdInfo.closures() == null) {
				return false;
			}

			boolean changed = false;
			for (IVariableBinding vb : new ArrayList<IVariableBinding>(
					createdInfo.closures())) {
				boolean outer = vb.getDeclaringMethod() == null ? !declaredWithin(
						scope, vb) : !vb.getDeclaringMethod().getDeclaringClass()
						.isEqualTo(creator.type());
				if (outer && !creator.isClosure(vb)) {
					creator.addClosure(vb);
					changed = true;
				}
			}
			return changed;
		}
	}

	/** The nearest class body around a node */
	private static ASTNode enclosingTypeBody(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof AbstractTypeDeclaration
					|| n instanceof AnonymousClassDeclaration) {
				return n;
			}
		}
		return null;
	}

	/** Whether a variable is declared inside scope; unknown counts as inside */
	private static boolean declaredWithin(ASTNode scope, IVariableBinding vb) {
		if (scope == null || !(scope.getRoot() instanceof CompilationUnit)) {
			return true;
		}
		ASTNode declaration = ((CompilationUnit) scope.getRoot())
				.findDeclaringNode(vb);
		if (declaration == null) {
			return true;
		}
		for (ASTNode n = declaration; n != null; n = n.getParent()) {
			if (n == scope) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (Modifier.isNative(node.getModifiers())) {
			typeInfo.setHasNatives();
		}

		if (TransformUtil.isMain(node.resolveBinding())) {
			typeInfo.setHasMain();
		}

		return true;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		captureSuperclass(node);
		return true;
	}

	@Override
	public boolean visit(RecordDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(RecordDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		typeInfo = typeInfo.parent();
	}


	@Override
	public boolean visit(Initializer node) {
		typeInfo.addInit(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;

			if (isClosure(node, vb)) {
				TypeInfo holder = typeInfo.closureHolder();
				if (holder != null) {
					holder.addClosure(vb);
				}
			}
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (TransformUtil.initInInit(node)) {
			typeInfo.addInit(node);
		}

		return super.visit(node);
	}

	private boolean isClosure(SimpleName node, IVariableBinding vb) {
		if (vb.isField()) {
			return false;
		}

		// A local of an initializer block is captured when it is declared
		// outside the class using it (the CLI has no Eclipse Java model)
		if (vb.getDeclaringMethod() == null
				&& declaredWithin(enclosingTypeBody(node), vb)) {
			return false;
		}

		// Java 8+ captures effectively final variables too, which lambdas
		// (rewritten into anonymous classes) almost always rely on
		if (!Modifier.isFinal(vb.getModifiers()) && !vb.isEffectivelyFinal()) {
			return false;
		}
		VariableDeclarationFragment vdf = initializer(node);
		if (vdf == null && vb.getDeclaringMethod() != null) {
			IMethodBinding pmb = parentMethod(node);
			if (pmb.isEqualTo(vb.getDeclaringMethod())) {
				// Final local variable
				return false;
			}
		}
		return true;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	private VariableDeclarationFragment initializer(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n.getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) n
						.getParent();
				if (type().isEqualTo(vdf.resolveBinding().getDeclaringClass())
						&& vdf.getInitializer() == n) {
					return vdf;
				}
			}
		}

		return null;
	}

	private ITypeBinding type() {
		return typeInfo.type();
	}
}
