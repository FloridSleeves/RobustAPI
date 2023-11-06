package checker.model;

import java.util.ArrayList;

public class Assignment {
	public String lhs;
	public ArrayList<String> rhs;

	public Assignment(String var, ArrayList<String> uses) {
		this.lhs = var;
		this.rhs = uses;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Assignment)) {
			return false;
		}

		Assignment that = (Assignment) o;
		return this.lhs.equals(that.lhs) && this.rhs.equals(that.rhs);
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash + 17 * this.lhs.hashCode();
		hash = hash + 31 * this.rhs.hashCode();
		return hash;
	}

	@Override
	public String toString() {
		return this.lhs + "->" + this.rhs.toString();
	}
}
