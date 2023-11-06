package checker.maple.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import checker.model.Pattern;

public class MySQLAccess {
	final String url = "jdbc:mysql://localhost:3306/maple?autoReconnect=true&useSSL=false";
	final String username = "root";
	// final String password = "Password69";
	// final String password = "Mihirmathur@01";
	final String password = "5887526";
	String table;
	Connection connect = null;
	Statement statement = null;
	public ResultSet result = null;
	PreparedStatement prep = null;

	public void connect() {
		try {
			// This will load the MySQL driver, each DB has its own driver
			Class.forName("com.mysql.jdbc.Driver");
			// Setup the connection with the DB
			connect = DriverManager.getConnection(url, username, password);
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
	}

	public HashSet<Pattern> getPatterns(String _method, String _class) {
		HashSet<Pattern> patterns = new HashSet<Pattern>();

		if (connect != null) {
			try {
				// construct the query
				String query;
				if (_class != null) {
					query = "select * from patterns where method='"
							+ _method + "' and class='" + _class + "';";
				} else {
					query = "select * from patterns where method='"
							+ _method + "';";
				}

				prep = connect.prepareStatement(query);
				result = prep.executeQuery();
				while (result.next()) {
					// populate HashMap with the results
					int id = result.getInt("id");
					String className = result.getString("class");
					String methodName = result.getString("method");
					String pattern = result.getString("pattern");
					int support = result.getInt("support");
					boolean isRequired = result.getBoolean("isRequired");
					String description = result.getString("description");
					int vote = result.getInt("votes");
					int downvotes = result.getInt("downvotes");
					String links = result.getString("links");
					Pattern p = new Pattern(id, className, methodName, pattern,
							support, isRequired, description,
							vote, downvotes, links);
					patterns.add(p);
				}

				result.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		return patterns;
	}

	public void addVote(int vote, int patternID) {
		if (connect != null) {
			try {
				// construct the update
				String update = "UPDATE patterns SET votes = votes +" + vote
						+ " WHERE id = " + patternID + ";";

				prep = connect.prepareStatement(update);
				prep.executeUpdate();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public void addDownvote(int downvote, int patternID) {
		if (connect != null) {
			try {
				// construct the update
				String update = "UPDATE patterns SET downvotes = downvotes +" + downvote
						+ " WHERE id = " + patternID + ";";

				prep = connect.prepareStatement(update);
				prep.executeUpdate();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	// for LinkExtractor
	public void addColumn(String columnName) {
		if (connect != null) {
			try {
				// construct the update
				String update = "ALTER TABLE patterns ADD " + columnName + ""
						+ " TEXT";

				prep = connect.prepareStatement(update);
				prep.executeUpdate();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	// for LinkExtractor
	public boolean patternExists(String _class, String _method, String _pattern) {
		result = null;

		if (connect != null) {
			try {
				// construct the query
				String query;
				query = "select * from patterns where method='"
						+ _method + "' and class='" + _class + "' and pattern='" + _pattern + "';";
				prep = connect.prepareStatement(query);
				result = prep.executeQuery();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		try {
			return result.next();
		} catch (SQLException e) {
			return false;
		}
	}

	// for LinkExtractor
	public void addValueToColumn(String _class, String _method, String _pattern,
			String _columnName, String _val) {
		if (connect != null) {
			try {
				// construct the update
				// String update = "UPDATE patterns SET " + _columnName + ""
				// + "='"+ _val + "' WHERE method='" + _method
				// + "' and class='"+ _class + "' and pattern='" + _pattern + "';";

				PreparedStatement ps = connect
						.prepareStatement("UPDATE patterns SET links=? WHERE method=? and class=? and pattern=?");

				// ps.setString(1, _columnName);
				ps.setString(1, _val);
				ps.setString(2, _method);
				ps.setString(3, _class);
				ps.setString(4, _pattern);

				ps.executeUpdate();
				ps.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public void close() {
		try {
			if (result != null)
				result.close();
			if (statement != null)
				statement.close();
			if (prep != null)
				prep.close();
			if (connect != null)
				connect.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
