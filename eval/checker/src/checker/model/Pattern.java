package checker.model;

public class Pattern {
	public int id;
	public String className;
	public String methodName;
	public String pattern;
	public int support;
	public boolean isRequired;
	public String description;
	public int vote;
	public int downvote;
	public String links;

	public Pattern(int id, String className, String methodName, String pattern,
			int support, boolean isRequired, String description, int vote, int downvote,
			String links) {
		this.id = id;
		this.className = className;
		this.methodName = methodName;
		this.pattern = pattern;
		this.support = support;
		this.isRequired = isRequired;
		this.description = description;
		this.vote = vote;
		this.downvote = downvote;
		this.links = links;
	}
}
