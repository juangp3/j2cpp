package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SourceProject {
	private final List<File> sourcePaths;
	private final List<File> classPaths;
	private final Map<String, SourceUnit> units = new HashMap<String, SourceUnit>();

	public SourceProject(Collection<File> sourcePaths,
			Collection<File> classPaths) throws IOException {
		this.sourcePaths = absolutePaths(sourcePaths);
		this.classPaths = absolutePaths(classPaths);
		indexSources();
	}

	private static List<File> absolutePaths(Collection<File> paths) {
		List<File> result = new ArrayList<File>();
		for (File path : paths) {
			result.add(path.getAbsoluteFile());
		}
		return result;
	}

	private void indexSources() throws IOException {
		for (File sourcePath : sourcePaths) {
			if (!sourcePath.isDirectory()) {
				continue;
			}
			indexSources(sourcePath);
		}
	}

	private void indexSources(File directory) throws IOException {
		File[] entries = directory.listFiles();
		if (entries == null) {
			throw new IOException("Could not read source directory "
					+ directory);
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				indexSources(entry);
			} else if (entry.getName().endsWith(".java")) {
				addUnit(entry);
			}
		}
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

	public Collection<SourceUnit> getUnits() {
		return units.values();
	}

	public CompilationUnit parse(SourceUnit unit, IProgressMonitor monitor)
			throws IOException {
		ASTParser parser = newParser();
		parser.setSource(read(unit.file()).toCharArray());
		parser.setUnitName(unitName(unit.file()));
		return (CompilationUnit) parser.createAST(monitor);
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

	private String unitName(File file) {
		for (File sourcePath : sourcePaths) {
			String root = sourcePath.getAbsolutePath();
			String path = file.getAbsolutePath();
			if (!root.endsWith(File.separator)) {
				root += File.separator;
			}
			if (path.startsWith(root)) {
				return path.substring(root.length()).replace(
						File.separatorChar, '/');
			}
		}
		return file.getName();
	}

	private static String read(File file) throws IOException {
		InputStream input = new FileInputStream(file);
		try {
			byte[] buffer = new byte[(int) file.length()];
			int offset = 0;
			while (offset < buffer.length) {
				int count = input.read(buffer, offset, buffer.length - offset);
				if (count < 0) {
					break;
				}
				offset += count;
			}
			return new String(buffer, 0, offset, Charset.defaultCharset());
		} finally {
			input.close();
		}
	}

	private String[] sourceEncodings() {
		String[] encodings = new String[sourcePaths.size()];
		for (int i = 0; i < encodings.length; i++) {
			encodings[i] = Charset.defaultCharset().name();
		}
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
