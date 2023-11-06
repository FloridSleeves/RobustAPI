package checker.model;

public class Receiver {
	public String method;
	public String obj;

	public Receiver(String obj, String method) {
		this.obj = obj;
		this.method = method;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Receiver)) {
			return false;
		}

		Receiver that = (Receiver) o;
		return this.obj.equals(that.obj) && this.method.equals(that.method);
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash + 17 * this.obj.hashCode();
		hash = hash + 31 * this.method.hashCode();
		return hash;
	}

	@Override
	public String toString() {
		return this.obj + "." + this.method;
	}
}
