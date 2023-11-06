package checker.maple.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

import checker.model.APICall;
import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.ControlConstruct;

public class APICallVisitor extends ASTVisitor {
	public ArrayList<APISeqItem> seq;
	public String condition;
	public ArrayList<String> exits; // remember the path conditions of all previous exits
	public HashMap<String, String> fields;
	public HashMap<String, String> locals;

	public APICallVisitor(HashMap<String, String> fields) {
		this.seq = new ArrayList<APISeqItem>();
		this.condition = "true";
		this.exits = new ArrayList<String>();
		this.fields = fields;
		this.locals = new HashMap<String, String>();
	}

	// build the symbol table
	@Override
	public boolean visit(SingleVariableDeclaration node) {
		Type t = node.getType();
		String var = node.getName().toString();
		String type = getType(t);
		locals.put(var, type);
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			locals.put(var, type);
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		Type t = node.getType();
		List<VariableDeclarationFragment> frags = node.fragments();
		String type = getType(t);
		for (VariableDeclarationFragment frag : frags) {
			String var = frag.getName().toString();
			locals.put(var, type);
		}
		return true;
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

	// visit method calls including regular method calls, calls to super
	// methods, and class instantiation
	public boolean visit(MethodInvocation node) {
		// System.out.println(node.getName().toString());

		// visit receiver first just in case if the receiver is also a method
		// call
		Expression expr = node.getExpression();
		String receiver = null;
		String type = null;
		if (expr != null) {
			receiver = expr.toString();
			expr.accept(this);
			if (locals.containsKey(receiver)) {
				type = locals.get(receiver);
			} else if (fields.containsKey(receiver)) {
				type = fields.get(receiver);
			} else {
				type = "unresolved";
			}
		}

		// visit arguments first to chain up any method calls in the argument
		// list
		List<Expression> args = node.arguments();
		ArrayList<String> arguments = new ArrayList<String>();
		for (Expression arg : args) {
			arguments.add(arg.toString());
			arg.accept(this);
		}

		String path_condition = condition;
		for (String exit : exits) {
			path_condition += " && !(" + exit + ")";
		}
		seq.add(new APICall(node.getName().toString(), path_condition, receiver, type, arguments));

		return false;
	}

	public boolean visit(SuperMethodInvocation node) {
		// System.out.println("super." + node.getName().toString());

		// visit arguments first to chain up any method calls in the argument
		// list
		List<Expression> args = node.arguments();
		ArrayList<String> arguments = new ArrayList<String>();
		for (Expression arg : args) {
			arguments.add(arg.toString());
			arg.accept(this);
		}

		String path_condition = condition;
		for (String exit : exits) {
			path_condition += " && !(" + exit + ")";
		}
		seq.add(new APICall("super." + node.getName().toString(), path_condition, null, null, arguments));

		return false;
	}

	public boolean visit(ClassInstanceCreation node) {
		// System.out.println("new " + node.getType().toString());

		// visit arguments first to chain up any method calls in the argument
		// list
		List<Expression> args = node.arguments();
		ArrayList<String> arguments = new ArrayList<String>();
		for (Expression arg : args) {
			arguments.add(arg.toString());
			arg.accept(this);
		}

		String path_condition = condition;
		for (String exit : exits) {
			path_condition += " && !(" + exit + ")";
		}
		seq.add(new APICall("new " + node.getType().toString(), path_condition, null, node.getType().toString(),
				arguments));

		return false;
	}

	// visit control-flow structures
	public boolean visit(TryStatement node) {
		// System.out.println("Try");
		seq.add(ControlConstruct.TRY);

		List<VariableDeclarationExpression> rsrcs = node.resources();
		for (VariableDeclarationExpression rsrc : rsrcs) {
			rsrc.accept(this);
		}

		node.getBody().accept(this);
		this.seq.add(ControlConstruct.END_BLOCK);

		List<CatchClause> catches = node.catchClauses();
		for (CatchClause c : catches) {
			c.accept(this);
		}

		Block fblock = node.getFinally();
		if (fblock != null) {
			// System.out.println("Finally");
			seq.add(ControlConstruct.FINALLY);
			fblock.accept(this);
			seq.add(ControlConstruct.END_BLOCK);
		}

		return false;
	}

	public boolean visit(CatchClause node) {
		// System.out.println("catch");
		seq.add(new CATCH(node.getException().getType().toString()));
		return true;
	}

	public void endVisit(CatchClause node) {
		this.seq.add(ControlConstruct.END_BLOCK);
	}

	public boolean visit(IfStatement node) {
		// System.out.println("If");
		seq.add(ControlConstruct.IF);

		String oldCond = condition;
		Expression expr = node.getExpression();
		// System.out.println(expr.toString());
		if (oldCond.equals("true")) {
			condition = expr.toString();
		} else {
			condition = oldCond + " && " + expr.toString();
		}

		// reset the exit flag for the if branch
		Statement thenStmt = node.getThenStatement();
		thenStmt.accept(this);
		this.seq.add(ControlConstruct.END_BLOCK);

		// reset the exit flag for the if branch
		Statement elseStmt = node.getElseStatement();
		if (elseStmt != null) {
			// System.out.println("else");
			if (oldCond.equals("true")) {
				condition = "!(" + expr.toString() + ")";
			} else {
				condition = oldCond + " && !(" + expr.toString() + ")";
			}

			seq.add(ControlConstruct.ELSE);
			elseStmt.accept(this);
			this.seq.add(ControlConstruct.END_BLOCK);
		}

		condition = oldCond;

		return false;
	}

	public boolean visit(ForStatement node) {
		// System.out.println("Loop");
		List<VariableDeclarationExpression> inits = node.initializers();
		for (VariableDeclarationExpression init : inits) {
			init.accept(this);
		}

		seq.add(ControlConstruct.LOOP);

		String oldCond = condition;
		Expression expr = node.getExpression();
		if (expr != null) {
			if (oldCond.equals("true")) {
				condition = expr.toString();
			} else {
				condition = oldCond + " && " + expr.toString();
			}
		}

		Statement stmt = node.getBody();
		stmt.accept(this);

		condition = oldCond;

		return false;
	}

	public void endVisit(ForStatement node) {
		this.seq.add(ControlConstruct.END_BLOCK);
	}

	public boolean visit(EnhancedForStatement node) {
		// System.out.println("Loop");
		seq.add(ControlConstruct.LOOP);

		Expression expr = node.getExpression();
		String oldCond = condition;
		if (oldCond.equals("true")) {
			String s = expr.toString();
			if (s.contains("keySet()")) {
				String rcv = s.substring(0, s.indexOf("keySet()") - 1);
				String arg = node.getParameter().getName().toString();
				condition = rcv + ".containsKey(" + arg + ") && " + rcv + ".size() > 0";
			} else {
				condition = expr.toString() + ".size() > 0";
			}
		} else {
			String s = expr.toString();
			if (s.contains("keySet()")) {
				String rcv = s.substring(0, s.indexOf("keySet()") - 1);
				String arg = node.getParameter().getName().toString();
				condition = oldCond + " && " + rcv + ".containsKey(" + arg + ") && " + rcv + ".size() > 0";
			} else {
				condition = oldCond + " && " + expr.toString() + ".size() > 0";
			}
		}

		SingleVariableDeclaration decl = node.getParameter();
		if (decl != null) {
			decl.accept(this);
		}

		Statement stmt = node.getBody();
		stmt.accept(this);

		condition = oldCond;

		return false;
	}

	public void endVisit(EnhancedForStatement node) {
		this.seq.add(ControlConstruct.END_BLOCK);
	}

	public boolean visit(WhileStatement node) {
		// System.out.println("Loop");
		seq.add(ControlConstruct.LOOP);

		Expression expr = node.getExpression();
		String oldCond = condition;
		if (expr != null) {
			if (oldCond.equals("true")) {
				condition = expr.toString();
			} else {
				condition = oldCond + " && " + expr.toString();
			}
		}

		Statement stmt = node.getBody();
		stmt.accept(this);

		condition = oldCond;

		return false;
	}

	public void endVisit(WhileStatement node) {
		this.seq.add(ControlConstruct.END_BLOCK);
	}

	public void endVisit(ReturnStatement node) {
		if (!condition.equals("true")
				&& !(node.getParent() instanceof Block && node.getParent().getParent() instanceof CatchClause)) {
			exits.add(condition);
		}
	}

	public void endVisit(ThrowStatement node) {
		if (!condition.equals("true")
				&& !(node.getParent() instanceof Block && node.getParent().getParent() instanceof CatchClause)) {
			exits.add(condition);
		}
	}
}
