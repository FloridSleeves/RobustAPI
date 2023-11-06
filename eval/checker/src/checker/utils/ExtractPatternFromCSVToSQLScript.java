package checker.utils;

public class ExtractPatternFromCSVToSQLScript {
	public static void main(String[] args) {
		String tsvPath = "/home/troy/research/Maple-extension/valid_patterns.tsv";

		String content = FileUtils.readFileToString(tsvPath);
		String[] lines = content.split(System.lineSeparator());
		final String insertPattern = "insert into patterns (id, class, method, pattern, support, isRequired, description) values (";
		// skip the first header row
		int id = 0;
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i];
			String[] columns = line.split("\t");
			if (columns.length < 8) {
				continue;
			}

			String sql = insertPattern + id + ", '"
					+ columns[0] + "', '" + columns[1] + "', '"
					+ columns[3] + "', " + columns[4] + ", ";

			if (columns[6].equals("1")) {
				sql += "TRUE";
			} else if (columns[6].equals("0")) {
				sql += "FALSE";
			} else {
				continue;
			}

			sql += ", '');";
			System.out.println(sql);
			id++;
		}
	}
}
