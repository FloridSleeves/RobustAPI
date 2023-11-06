package checker.model;

public class CATCH implements APISeqItem {
	public String type;

	public CATCH(String type) {
		this.type = type;
	}

	@Override
	public int hashCode() {
		int hash = 31;
		hash += 37 * this.type.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CATCH) {
			CATCH catchBlock = (CATCH) obj;
			return this.type.equals(catchBlock.type);
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return "CATCH(" + type + ")";
	}
}
