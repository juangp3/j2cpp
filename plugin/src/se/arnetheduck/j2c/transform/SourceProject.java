package se.arnetheduck.j2c.transform;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SourceProject {
	private final List<File> sourcePaths;
	private final List<File> classPaths;
	private final Charset encoding;
	private final Map<String, SourceUnit> units = new HashMap<String, SourceUnit>();

	/** Receives each parsed unit of a batch */
	public interface UnitHandler {
		void accept(SourceUnit unit, CompilationUnit ast) throws Exception;
	}

	public SourceProject(Collection<File> sourcePaths,
			Collection<File> classPaths, Charset encoding) {
		this.sourcePaths = absolutePaths(sourcePaths);
		this.classPaths = absolutePaths(classPaths);
		this.encoding = encoding;
	}

	private static List<File> absolutePaths(Collection<File> paths) {
		List<File> result = new ArrayList<File>();
		for (File path : paths) {
			result.add(path.getAbsoluteFile());
		}
		return result;
	}

	private SourceUnit addUnit(File file) {
		String path = file.getAbsoluteFile().getPath();
		SourceUnit unit = units.get(path);
		if (unit == null) {
			unit = new SourceUnit(file);
			units.put(path, unit);
		}
		return unit;
	}

	public SourceUnit getUnit(File file) {
		return addUnit(file);
	}

	/**
	 * Parses the units with one shared compiler environment, which is much
	 * cheaper than resolving bindings for each file separately. Handler
	 * failures stop the remaining units from being handled and are rethrown.
	 */
	public void parse(Collection<SourceUnit> batch, IProgressMonitor monitor,
			final UnitHandler handler) throws Exception {
		final Map<String, SourceUnit> byPath = new HashMap<String, SourceUnit>();
		for (SourceUnit unit : batch) {
			byPath.put(unit.file().getPath(), unit);
		}
		String[] paths = byPath.keySet().toArray(new String[byPath.size()]);
		Arrays.sort(paths);
		String[] encodings = new String[paths.length];
		Arrays.fill(encodings, encoding.name());

		final Exception[] failure = new Exception[1];
		newParser().createASTs(paths, encodings, new String[0],
				new FileASTRequestor() {
					@Override
					public void acceptAST(String sourceFilePath,
							CompilationUnit ast) {
						if (failure[0] != null) {
							return;
						}
						try {
							handler.accept(byPath.get(sourceFilePath), ast);
						} catch (Exception e) {
							failure[0] = e;
						}
					}
				}, monitor);
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	public ITypeBinding resolve(String typeName) {
		ASTParser parser = newParser();
		parser.setSource(("class __J2CProbe { " + typeName.replace('$', '.')
				+ " value; }").toCharArray());
		parser.setUnitName("__J2CProbe.java");
		CompilationUnit probe = (CompilationUnit) parser.createAST(null);
		ITypeBinding binding = probe.getAST().resolveWellKnownType(typeName);
		if (binding != null) {
			return binding;
		}

		TypeDeclaration declaration = (TypeDeclaration) probe.types().get(0);
		return declaration.getFields()[0].getType().resolveBinding();
	}

	private ASTParser newParser() {
		ASTParser parser = ASTParser.newParser(AST.JLS17);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(paths(classPaths), paths(sourcePaths),
				sourceEncodings(), true);
		Map<String, String> options = new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		return parser;
	}

	private String[] sourceEncodings() {
		String[] encodings = new String[sourcePaths.size()];
		Arrays.fill(encodings, encoding.name());
		return encodings;
	}

	private static String[] paths(List<File> paths) {
		String[] result = new String[paths.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = paths.get(i).getPath();
		}
		return result;
	}

	public SourceUnit sourceUnit(ITypeBinding binding) {
		if (binding == null || binding.isArray() || binding.isPrimitive()) {
			return null;
		}

		String binaryName = binding.getErasure().getBinaryName();
		if (binaryName == null || binaryName.length() == 0) {
			return null;
		}

		int nested = binaryName.indexOf('$');
		if (nested >= 0) {
			binaryName = binaryName.substring(0, nested);
		}
		int packageEnd = binaryName.lastIndexOf('.');
		String packagePath = packageEnd < 0 ? ""
				: binaryName.substring(0, packageEnd).replace('.',
						File.separatorChar);
		String typeName = packageEnd < 0 ? binaryName
				: binaryName.substring(packageEnd + 1);
		for (File sourcePath : sourcePaths) {
			File source = new File(new File(sourcePath, packagePath),
					typeName + ".java");
			if (source.isFile()) {
				return addUnit(source);
			}
		}

		return null;
	}
}
