package checker.maple.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class APITypeVisitor extends ASTVisitor {
	public HashSet<String> types;
	public HashMap<String, String> map;

	public APITypeVisitor() {
		types = new HashSet<String>();
		map = new HashMap<String, String>();
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		Type t = node.getType();
		String var = node.getName().toString();
		String type = getType(t);
		map.put(var, type);
		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			map.put(var, type);
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			map.put(var, type);
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			map.put(var, type);
		}
		return false;
	}

	private String getType(Type node) {
		String type = node.toString();
		if (node.isNameQualifiedType()) {
			types.add(type);
			QualifiedType qtype = (QualifiedType) node;
			type = qtype.getName().toString();
			types.add(type);
		} else if (node.isParameterizedType()) {
			types.add(type);
			ParameterizedType ptype = (ParameterizedType) node;
			type = ptype.getType().toString();
			types.add(type);
			List<Type> params = ptype.typeArguments();
			for (Type param : params) {
				getType(param);
			}
		} else {
			types.add(type);
		}

		return type;
	}
}
