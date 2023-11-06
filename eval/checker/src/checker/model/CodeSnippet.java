package checker.model;

public class CodeSnippet {

    private String id;
    private String snippet;

    public String getId() {
        return id;
    }

    public void setId(String _id) {
        this.id = _id;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String _snippet) {
        this.snippet = _snippet;
    }

    public String toString() {
        return "ID: " + id + ", snippet: " + snippet;
    }

    @Override
    public int hashCode() {
        int hash = 31;
        hash += 17 * id.hashCode();
        hash += 43 * snippet.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CodeSnippet && ((CodeSnippet) obj).getId().equals(id)
                && ((CodeSnippet) obj).getSnippet().equals(snippet);
    }
}
