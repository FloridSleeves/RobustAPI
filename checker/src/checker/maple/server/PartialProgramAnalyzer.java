package checker.maple.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

import checker.model.APICall;
import checker.model.APISeqItem;

public class PartialProgramAnalyzer {
	CompilationUnit cu;
	public boolean isIncomplete = false;

	public PartialProgramAnalyzer(String snippet) throws Exception {
		PartialProgramParser parser = new PartialProgramParser();
		// unescape html special characters, e.g., &amp;&amp; will become &&
		String code = StringEscapeUtils.unescapeHtml4(snippet);
		this.cu = parser.getCompilationUnitFromString(code);
		this.isIncomplete = parser.cutype == 0 ? false : true;
		if (this.cu == null) {
			System.out.println("25:");
			throw new Exception("Partial Program Parse Error!");
		} else {
			IProblem[] errors = this.cu.getProblems();
			if (errors != null && errors.length != 0) {
				System.out.println(errors[0]);
				throw new Exception("Partial Program Parse Error!");
			}
		}
	}

	public HashMap<String, ArrayList<APISeqItem>> retrieveAPICallSequences() {
		if (this.cu == null) {
			return null;
		}

		FieldVisitor fv = new FieldVisitor();
		this.cu.accept(fv);

		MethodVisitor mv = new MethodVisitor(fv.fields);
		this.cu.accept(mv);
		return mv.seqs;
	}

	public ArrayList<ArrayList<APISeqItem>> retrieveAPICallSequences(HashSet<String> apis) {
		if (this.cu == null) {
			return null;
		}

		FieldVisitor fv = new FieldVisitor();
		this.cu.accept(fv);

		MethodVisitor mv = new MethodVisitor(fv.fields);
		this.cu.accept(mv);
		ArrayList<ArrayList<APISeqItem>> seqs = new ArrayList<ArrayList<APISeqItem>>();
		for (String method : mv.seqs.keySet()) {
			ArrayList<APISeqItem> seq = mv.seqs.get(method);
			ArrayList<String> calls = new ArrayList<String>();
			for (APISeqItem item : seq) {
				if (item instanceof APICall) {
					calls.add(((APICall) item).name);
				}
			}

			if (calls.containsAll(apis)) {
				seqs.add(seq);
			}
		}

		return seqs;
	}

	public APITypeVisitor resolveTypes() {
		if (this.cu == null) {
			return null;
		}

		APITypeVisitor visitor = new APITypeVisitor();
		this.cu.accept(visitor);
		return visitor;
	}
}
