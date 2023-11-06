package checker.model;

public class Predicate {
	public String api;
	public String condition;

	public Predicate(String method, String predicate) {
		this.api = method;
		this.condition = predicate;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Predicate)) {
			return false;
		}

		Predicate that = (Predicate) o;
		return this.api.equals(that.api) && this.condition.equals(that.condition);
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash + 17 * this.api.hashCode();
		hash = hash + 31 * this.condition.hashCode();
		return hash;
	}

	@Override
	public String toString() {
		return this.api + ":" + this.condition;
	}
}
