package checker.maple.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;

import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.ControlConstruct;

public class MethodVisitor extends ASTVisitor {
	public HashMap<String, ArrayList<APISeqItem>> seqs = new HashMap<String, ArrayList<APISeqItem>>();
	public HashMap<String, String> fields;

	public MethodVisitor(HashMap<String, String> fields) {
		this.fields = fields;
	}

	public boolean visit(MethodDeclaration node) {
		String method = node.getName().toString();

		List<Type> excpts = node.thrownExceptionTypes();
		boolean flag = false;
		if (excpts != null && !excpts.isEmpty()) {
			flag = true;
		}

		APICallVisitor cv = new APICallVisitor(fields);
		node.accept(cv);
		if (flag) {
			// this method throws exception
			cv.seq.add(0, ControlConstruct.TRY);
			cv.seq.add(ControlConstruct.END_BLOCK);
			for (Type t : excpts) {
				cv.seq.add(new CATCH(t.toString()));
				cv.seq.add(ControlConstruct.END_BLOCK);
			}
		}
		seqs.put(method, cv.seq);

		return false;
	}
}
