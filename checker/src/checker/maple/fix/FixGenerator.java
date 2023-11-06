package checker.maple.fix;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import checker.model.APICall;
import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.ControlConstruct;

public class FixGenerator {

	final String indentS = "  ";

	public String generate(ArrayList<APISeqItem> pattern, ArrayList<APISeqItem> mSeq, APICall focalAPICall) {
		String fix = "";
		int indent = 0;
		boolean hasSynthesizedGuard = false;
		for (int i = 0; i < pattern.size(); i++) {
			APISeqItem item = pattern.get(i);
			if (item instanceof ControlConstruct) {
				if (item.equals(ControlConstruct.IF)) {
					// look forward to find an API call with guard condition
					String guard = getContextualizedGuardCondition(i, pattern, mSeq);
					if (guard != null) {
						for (int j = 0; j < indent; j++) {
							fix += indentS;
						}
						fix += "if (" + guard + ") {";
						indent++;
					}
					hasSynthesizedGuard = true;
				} else if (item.equals(ControlConstruct.ELSE)) {
					fix += " else {";
					indent++;
				} else if (item.equals(ControlConstruct.TRY)) {
					for (int j = 0; j < indent; j++) {
						fix += indentS;
					}
					fix += "try {";
					indent++;
				} else if (item.equals(ControlConstruct.LOOP)) {
					// look forward to find an API call with guard condition
					String guard = getContextualizedGuardCondition(i, pattern, mSeq);
					if (guard != null) {
						for (int j = 0; j < indent; j++) {
							fix += indentS;
						}
						fix += "while (" + guard + ") {";
						indent++;
					}
					hasSynthesizedGuard = true;
				} else if (item.equals(ControlConstruct.FINALLY)) {
					fix += " finally {";
					indent++;
				} else if (item.equals(ControlConstruct.END_BLOCK)) {
					indent--;
					for (int j = 0; j < indent; j++) {
						fix += indentS;
					}
					fix += "}";
				}
			} else if (item instanceof CATCH) {
				CATCH catchClause = (CATCH) item;
				fix += " catch (" + catchClause.type + " e) {";
				indent++;
			} else {
				APICall call = (APICall) item;
				APICall counterpart = findCounterpartCall(call, mSeq);

				if (!hasSynthesizedGuard && !call.condition.equals("true")) {
					// synthesize an if guard
					String guard = getContextualizedGuardCondition(call.condition, counterpart);
					for (int j = 0; j < indent; j++) {
						fix += indentS;
					}
					fix += "if (" + guard + ") {" + System.lineSeparator();
					indent++;
				}

				for (int j = 0; j < indent; j++) {
					fix += indentS;
				}

				if (counterpart != null) {
					if (counterpart.receiver != null) {
						fix += counterpart.receiver + ".";
					}
					fix += counterpart.name + "(";
					for (int j = 0; j < counterpart.arguments.size(); j++) {
						if (j == counterpart.arguments.size() - 1) {
							fix += counterpart.arguments.get(j);
						} else {
							fix += counterpart.arguments.get(j) + ",";
						}
					}
					fix += ");";
				} else {
					if (call.receiver_type != null) {
						if (call.receiver_type.equals("unresolved")) {
							fix += "var." + call.name + "(";
						} else {
							fix += call.receiver_type.toLowerCase() + "." + call.name + "(";
						}
					} else {
						if (call.name.startsWith("new ")) {
							// constructor
							fix += call.name + "(";
						} else {
							fix += focalAPICall.receiver + "." + call.name + "(";
						}

					}

					for (int j = 0; j < call.arguments.size(); j++) {
						if (j == call.arguments.size() - 1) {
							fix += "arg" + j;
						} else {
							fix += "arg" + j + ",";
						}
					}
					fix += ");";
				}

				if (!hasSynthesizedGuard && !call.condition.equals("true")) {
					fix += System.lineSeparator();
					indent--;
					for (int j = 0; j < indent; j++) {
						fix += indentS;
					}
					fix += "}";
				}
			}

			if (i + 1 < pattern.size()) {
				APISeqItem next = pattern.get(i + 1);
				if (!(next instanceof ControlConstruct
						&& (next == ControlConstruct.ELSE || next == ControlConstruct.FINALLY))
						&& !(next instanceof CATCH)) {
					fix += System.lineSeparator();
				}
			}
		}

		return fix;
	}

	private String getContextualizedGuardCondition(int startIndex, ArrayList<APISeqItem> pattern,
			ArrayList<APISeqItem> seq) {
		APICall theCall = null;
		for (int j = startIndex + 1; j < pattern.size(); j++) {
			APISeqItem next = pattern.get(j);
			if (next instanceof APICall && !((APICall) next).condition.equals("true")) {
				theCall = (APICall) next;
				break;
			}
		}

		String guard = null;
		if (theCall != null) {
			// find the same API call in the snippet to contextualize the guard condition
			APICall counterpart = findCounterpartCall(theCall, seq);
			if (counterpart != null) {
				guard = theCall.condition;

				// contextualize the receiver name in the API call
				guard = getContextualizedGuardCondition(guard, counterpart);
			}
		}

		return guard;
	}

	private String getContextualizedGuardCondition(String guard, APICall counterpartCall) {
		String contextualizedGuard = guard;
		// contextualize the receiver name in the API call
		if (contextualizedGuard.contains("rcv")) {
			contextualizedGuard = contextualizedGuard.replaceAll("rcv", counterpartCall.receiver);
		}

		// TODO: contextualize the argument names in the API call
		String regex = "arg\\d";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(contextualizedGuard);
		while (matcher.find()) {
			String group = matcher.group();
			String indexS = group.substring(3);
			try {
				int index = Integer.parseInt(indexS);
				if (index < counterpartCall.arguments.size()) {
					String arg = counterpartCall.arguments.get(index);
					contextualizedGuard = contextualizedGuard.replaceAll(group, arg);
				}
			} catch (NumberFormatException e) {
				System.err.println("Cannot match the abstracted argument name " + indexS);
				continue;
			}
		}

		return contextualizedGuard;
	}

	private APICall findCounterpartCall(APICall call, ArrayList<APISeqItem> seq) {
		APICall counterpart = null;
		for (APISeqItem item : seq) {
			if (item instanceof APICall) {
				APICall other = (APICall) item;
				if (other.name.equals(call.name)) {
					counterpart = other;
					break;
				}
			}
		}

		return counterpart;
	}
}
