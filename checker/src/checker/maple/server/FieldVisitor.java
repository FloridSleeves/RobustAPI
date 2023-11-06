package checker.maple.server;

import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class FieldVisitor extends ASTVisitor {
	public HashMap<String, String> fields;

	public FieldVisitor() {
		this.fields = new HashMap<String, String>();
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			fields.put(var, type);
		}
		return false;
	}

	private String getType(Type node) {
		String type = node.toString();
		if (node.isNameQualifiedType()) {
			QualifiedType qtype = (QualifiedType) node;
			type = qtype.getName().toString();
		} else if (node.isParameterizedType()) {
			ParameterizedType ptype = (ParameterizedType) node;
			type = ptype.getType().toString();
			// List<Type> params = ptype.typeArguments();
			// for(Type param : params) {
			// getType(param);
			// }
		}

		return type;
	}
}
