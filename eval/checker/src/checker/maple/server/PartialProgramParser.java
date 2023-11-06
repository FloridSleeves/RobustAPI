package checker.maple.server;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class PartialProgramParser {
	public int cutype;
	private int flag = 0;

	public CompilationUnit getCompilationUnitFromString(String code)
			throws IOException, NullPointerException, ClassNotFoundException {
		ASTParser parser = getASTParser(code);
		ASTNode cu = (CompilationUnit) parser.createAST(null);
		// System.out.println(cu);
		cutype = 0;
		if (((CompilationUnit) cu).types().isEmpty()) {
			// Try to add class header + method header
			String s1 = "public class sample{\n public void foo(){\n" + code + "\n}\n}";
			cutype = 1;
			parser = getASTParser(s1);
			try {
				cu = parser.createAST(null);
			} catch (Exception e) {
				// parse error
				return null;
			}
			if (((CompilationUnit) cu).types().isEmpty() || ((CompilationUnit) cu).getProblems() != null
					&& ((CompilationUnit) cu).getProblems().length != 0) {
				// Try to add class header
				s1 = "public class sample{\n" + code + "\n}";
				parser = getASTParser(s1);
				try {
					cu = parser.createAST(null);
				} catch (Exception e) {
					System.out.println("33: parse error");
					// parse error
					return null;
				}
			}
		} else {
			// this code snippet has both class header and method header
			cutype = 0;
			parser = getASTParser(code);
			try {
				cu = parser.createAST(null);
			} catch (Exception e) {
				// parse error
				return null;
			}
		}
		return (CompilationUnit) cu;
	}

	private ASTParser getASTParser(String sourceCode) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setStatementsRecovery(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(sourceCode.toCharArray());
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		return parser;
	}
}
