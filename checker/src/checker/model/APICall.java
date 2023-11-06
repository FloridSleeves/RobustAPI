package checker.model;

import java.util.ArrayList;

public class APICall implements APISeqItem {
	public String name;
	public String condition;
	public String receiver;
	public String receiver_type;
	// If this is in a pattern, arguments list is a list of argument types.
	// If this is in a method call sequence, arguments list is a list of real
	// arguments.
	public ArrayList<String> arguments;

	public APICall(String name, String condition, String receiver, String type, ArrayList<String> args) {
		this.name = name;
		this.condition = condition;
		this.receiver = receiver;
		this.arguments = args;
		this.receiver_type = type;
	}

	public String getName() {
		String str = name;
		// str = str.substring(0, str.length() - 3);
		return str;
	}

	@Override
	public String toString() {
		String s = name + "(";
		for (int i = 0; i < arguments.size(); i++) {
			if (i == arguments.size() - 1) {
				s += arguments.get(i);
			} else {
				s += arguments.get(i) + ",";
			}

		}
		s += ")@" + condition;
		return s;
	}

	@Override
	public int hashCode() {
		int hash = 31;
		hash += 37 * this.name.hashCode();
		hash += 43 * this.condition.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof APICall) {
			APICall call = (APICall) obj;
			return this.name.equals(call.name) && this.condition.equals(call.condition);
		} else {
			return false;
		}
	}
}
