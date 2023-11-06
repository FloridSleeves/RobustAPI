package checker.utils;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SAT {
	static final Pattern METHOD_CALL_START = Pattern
			.compile("[a-zA-Z0-9_]+(\\s)*\\(");

	private HashMap<String, String> bool_symbol_map;
	private HashMap<String, String> int_symbol_map;
	private HashMap<String, String> call_symbol_map;
	private String temp;

	public SAT() {
		bool_symbol_map = new HashMap<String, String>();
		int_symbol_map = new HashMap<String, String>();
		call_symbol_map = new HashMap<String, String>();
		// int i = 0;
		// temp = "/home/troy/temp" + i + ".z3";
		// File f = new File(temp);
		// while(f.exists()) {
		// i++;
		// temp = "/home/troy/temp" + i + ".z3";
		// f = new File(temp);
		// }
		temp = "/tmp/temp.z3";
		// temp = "C:\\Users\\ajrei_000\\temp.z3";
	}

	/***** Check Equivalence *****/

	public boolean checkEquivalence(String p1, String p2) {
		// clear previous maps
		bool_symbol_map.clear();
		int_symbol_map.clear();
		call_symbol_map.clear();

		String p1_norm = normalize(p1);
		String p2_norm = normalize(p2);

		if (p1_norm.contains("?") || p2_norm.contains("?")) {
			return false;
		}

		// replace variable names and function calls with boolean and integer
		// symbols consistently. because Z3 does not support function calls and
		// we also need to know the type of each variables and subexpressions.
		String p1_sym = symbolize(p1_norm);
		String p2_sym = symbolize(p2_norm);

		// handle symbolizing conflicts
		HashSet<String> set1 = new HashSet<String>(bool_symbol_map.keySet());
		HashSet<String> set2 = new HashSet<String>(int_symbol_map.keySet());
		set1.retainAll(set2);
		if (!set1.isEmpty()) {
			// conflicts. this because we cannot distinguish whether == and != are comparing
			// two booleans or two integers
			for (String name : set1) {
				String symbol = int_symbol_map.get(name);
				p1_sym = p1_sym.replace(symbol, bool_symbol_map.get(name));
				p2_sym = p2_sym.replace(symbol, bool_symbol_map.get(name));
				int_symbol_map.remove(name);

				// need to find the associated boolean variable it compares with
				String another = null;
				for (String name2 : int_symbol_map.keySet()) {
					String regex1 = "^.*" + Pattern.quote(name) + "(\\s)*(=|\\!)=(\\s)*" + Pattern.quote(name2) + ".*$";
					String regex2 = "^.*" + Pattern.quote(name2) + "(\\s)*(=|\\!)=(\\s)*" + Pattern.quote(name) + ".*$";
					if (p1_norm.matches(regex1) || p1_norm.matches(regex2) || p2_norm.matches(regex1)
							|| p2_norm.matches(regex2)) {
						// name2 is the one
						another = name2;
						break;
					}
				}

				if (another != null) {
					String symbol2 = int_symbol_map.get(another);
					int_symbol_map.remove(another);
					if (!bool_symbol_map.containsKey(another)) {
						bool_symbol_map.put(another, "b" + bool_symbol_map.values().size());
						p1_sym = p1_sym.replace(symbol2, bool_symbol_map.get(another));
						p2_sym = p2_sym.replace(symbol2, bool_symbol_map.get(another));
					}
				}
			}
		}

		// Z3 does not support ++ or -- operators, replace it
		p1_sym = normalizePlusPlusAndMinusMinus(p1_sym);
		p2_sym = normalizePlusPlusAndMinusMinus(p2_sym);

		// convert infix expressions to prefix expressions because Z3 encodes
		// expression in prefix order
		try {
			String p1_prefix = InfixToPrefixConvertor.infixToPrefixConvert(p1_sym);
			String p2_prefix = InfixToPrefixConvertor.infixToPrefixConvert(p2_sym);

			// For expression A and B, encode them in the format of (A && !B) || (!A && B)
			// in Z3 to
			// check semantic equivalence
			String query = generateEquvalenceQueryInZ3(p1_prefix, p2_prefix);
			return !isSAT(query);
		} catch (Exception e) {
			// conversion error
			return true;
		}
	}

	private String normalizePlusPlusAndMinusMinus(String expr) {
		if (expr.contains("++")) {
			expr = expr.replaceAll("\\+\\+(?=i|\\d)|\\+\\+\\s(?=i|\\d)", "");
			expr = expr.replaceAll("(?<=\\d)\\+\\+|(?<=\\d)\\s\\+\\+", "");
		}

		if (expr.contains("--")) {
			expr = expr.replaceAll("--(?=i|\\d)|--\\s(?=i|\\d)", "");
			expr = expr.replaceAll("(?<=\\d)--|(?<=\\d)\\s--", "");
		}

		return expr;
	}

	public String generateEquvalenceQueryInZ3(String p1, String p2) {
		String p1_smt = encodeToSMTLibStandard(p1);
		String p2_smt = encodeToSMTLibStandard(p2);

		String query = "";
		// dump boolean symbols to a hashset to remove duplicated symbols
		HashSet<String> bool_sym_set = new HashSet<String>(bool_symbol_map.values());
		// declare boolean symbols in Z3
		for (String sym : bool_sym_set) {
			query += "(declare-const " + sym + " Bool)"
					+ System.lineSeparator();
		}

		// dump integer symbols to a hashset to remove duplicated symbols
		HashSet<String> int_sym_set = new HashSet<String>(int_symbol_map.values());
		for (String sym : int_sym_set) {
			query += "(declare-const " + sym + " Int)" + System.lineSeparator();
		}

		query += "(assert (or (and " + p1_smt + " (not " + p2_smt + ")) (and (not " + p1_smt + ") " + p2_smt + ")))"
				+ System.lineSeparator();
		query += "(check-sat)";
		return query;
	}

	/***** Check Implication *****/

	/**
	 * Check if p1 => p2
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public boolean checkImplication(String p1, String p2) {
		// clear previous maps
		bool_symbol_map.clear();
		int_symbol_map.clear();
		call_symbol_map.clear();

		String p1_norm = normalize(p1);
		String p2_norm = normalize(p2);

		if (p1_norm.contains("?") || p2_norm.contains("?")) {
			return false;
		}

		String p1_sym = symbolize(p1_norm);
		String p2_sym = symbolize(p2_norm);

		// handle symbolizing conflicts
		HashSet<String> set1 = new HashSet<String>(bool_symbol_map.keySet());
		HashSet<String> set2 = new HashSet<String>(int_symbol_map.keySet());
		set1.retainAll(set2);
		if (!set1.isEmpty()) {
			// conflicts. this because we cannot distinguish whether == and != are comparing
			// two booleans or two integers
			for (String name : set1) {
				String symbol = int_symbol_map.get(name);
				p1_sym = p1_sym.replace(symbol, bool_symbol_map.get(name));
				p2_sym = p2_sym.replace(symbol, bool_symbol_map.get(name));
				int_symbol_map.remove(name);

				// need to find the associated boolean variable it compares with
				String another = null;
				for (String name2 : int_symbol_map.keySet()) {
					String regex1 = "^.*" + Pattern.quote(name) + "(\\s)*(=|\\!)=(\\s)*" + Pattern.quote(name2) + ".*$";
					String regex2 = "^.*" + Pattern.quote(name2) + "(\\s)*(=|\\!)=(\\s)*" + Pattern.quote(name) + ".*$";
					if (p1_norm.matches(regex1) || p1_norm.matches(regex2) || p2_norm.matches(regex1)
							|| p2_norm.matches(regex2)) {
						// name2 is the one
						another = name2;
						break;
					}
				}

				if (another != null) {
					String symbol2 = int_symbol_map.get(another);
					int_symbol_map.remove(another);
					if (!bool_symbol_map.containsKey(another)) {
						bool_symbol_map.put(another, "b" + bool_symbol_map.values().size());
						p1_sym = p1_sym.replace(symbol2, bool_symbol_map.get(another));
						p2_sym = p2_sym.replace(symbol2, bool_symbol_map.get(another));
					}
				}
			}
		}

		// Z3 does not support ++ or -- operators, replace it
		p1_sym = normalizePlusPlusAndMinusMinus(p1_sym);
		p2_sym = normalizePlusPlusAndMinusMinus(p2_sym);

		try {
			String p1_prefix = InfixToPrefixConvertor.infixToPrefixConvert(p1_sym);
			String p2_prefix = InfixToPrefixConvertor.infixToPrefixConvert(p2_sym);

			String query = generateImplicationQueryInZ3(p1_prefix, p2_prefix);
			return !isSAT(query);
		} catch (Exception e) {
			// conversion error
			return true;
		}
	}

	public String generateImplicationQueryInZ3(String p1, String p2) {
		String p1_smt = encodeToSMTLibStandard(p1);
		String p2_smt = encodeToSMTLibStandard(p2);

		String query = "";
		// dump boolean symbols to a hashset to remove duplicated symbols
		HashSet<String> bool_sym_set = new HashSet<String>(bool_symbol_map.values());
		// declare boolean symbols in Z3
		for (String sym : bool_sym_set) {
			query += "(declare-const " + sym + " Bool)"
					+ System.lineSeparator();
		}

		// dump integer symbols to a hashset to remove duplicated symbols
		HashSet<String> int_sym_set = new HashSet<String>(int_symbol_map.values());
		for (String sym : int_sym_set) {
			query += "(declare-const " + sym + " Int)" + System.lineSeparator();
		}

		query += "(assert (and " + p1_smt + " (not " + p2_smt + ")))"
				+ System.lineSeparator();
		query += "(check-sat)";
		return query;
	}

	/***** General Utility *****/

	public boolean isSAT(String query) {
		FileUtils.writeStringToFile(query, temp);

		// run Z3
		String output = "";
		BufferedReader stdInput = null;
		try {
			Process p = Runtime.getRuntime().exec("z3 " + temp);
			stdInput = new BufferedReader(new InputStreamReader(
					p.getInputStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				output += s;
			}

			p.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (stdInput != null) {
				try {
					stdInput.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		boolean result;
		if (output.equals("sat")) {
			result = true;
		} else if (output.equals("unsat")) {
			result = false;
		} else {
			// throw new Exception("Z3 formatting error.");
			System.err.println("Z3 Formating error!");
			// delete the temporary file
			FileUtils.delete(temp);

			// let it elapse
			return true;
		}

		// delete the temporary file
		FileUtils.delete(temp);

		return result;
	}

	private String encodeToSMTLibStandard(String expr) {
		String rel = expr;
		// replace && with and
		rel = rel.replaceAll("&&", "and");
		// replace || with or
		rel = rel.replaceAll("\\|\\|", "or");
		// replace ! with not
		rel = rel.replaceAll("\\!(?!=)", "not");
		// replace == with =
		rel = rel.replaceAll("==", "=");
		// replace / with div
		rel = rel.replaceAll("\\/", "div");

		rel = rel.replace("%", "mod");
		return rel;
	}

	/**
	 * 
	 * Support conditional expression with logic operators (i.e., !, &&, ||) and
	 * arithmetic operators (i.e., *, /, +, -) We treat objects integers.
	 * Specifically, null is encoded as 0. For example,
	 * rcv.getA() != null is encoded as X != 0.
	 * 
	 * @param expr
	 * @return
	 */
	public String symbolize(String expr) {
		// first tokenize this expression by logic operators
		String[] arr = expr.split("&&|\\|\\||\\!(?!=)");

		for (String e : arr) {
			e = e.trim();

			if (e.isEmpty() || e.equals("(") || e.equals(")")) {
				continue;
			} else {
				e = stripUnbalancedParentheses(e);
			}

			if (e.contains("+") || e.contains("-") || e.contains("*")
					|| e.contains("/") || e.contains(">") || e.contains("<")
					|| e.contains(">=") || e.contains("<=") || e.contains("==")
					|| e.contains("!=") || e.contains("%")) {
				// this subexpression contains arithmetic operators.
				// separator order matters!!!
				String[] arr2 = e.split("\\+|-|\\*|\\/|>=|<=|>|<|==|\\!=|%");

				boolean isBooleanExpression = false;
				for (String sub : arr2) {
					String temp = sub.trim();
					temp = stripUnbalancedParentheses(temp);
					if (temp.trim().equals("true") || temp.trim().equals("false")) {
						isBooleanExpression = true;
					}
				}

				if (isBooleanExpression) {
					// treat these sub-expressions as booleans
					for (String sub : arr2) {
						sub = sub.trim();
						// strip unbalanced parentheses
						sub = stripUnbalancedParentheses(sub);

						if (sub.trim().equals("false") || sub.trim().equals("true")) {
							continue;
						} else {
							if (bool_symbol_map.containsKey(sub)) {
								continue;
							} else {
								String temp = stripUnnecessaryParentheses(sub);
								if (bool_symbol_map.containsKey(temp)) {
									String sym = bool_symbol_map.get(temp);
									bool_symbol_map.put(sub, sym);
								} else {
									String sym = "b" + bool_symbol_map.size();
									bool_symbol_map.put(sub, sym);
									if (!sub.equals(temp)) {
										// parentheses have been stripped off, also put the
										// stripped off version into the map
										bool_symbol_map.put(temp, sym);
									}
								}
							}
						}
					}
				} else {
					// treat these sub-subexpressions as integers
					for (String sub : arr2) {
						sub = sub.trim();
						if (sub.isEmpty()) {
							// saw this happen when there is a negative integer like -1
							// the negative sign is treated as a subtraction operator and split, leading to
							// an empty string between - and 1
							continue;
						}
						// strip unbalanced parentheses
						sub = stripUnbalancedParentheses(sub);

						if (sub.isEmpty()) {
							// saw this happen when there is a parenthesis before a negative integer like
							// (-1
							// the negative sign is treated as a subtraction operator and split, leading to
							// a lingering (
							// after stripping unbalanced parentheses, it becomes empty
							continue;
						}

						if (sub.matches("^\\d+$")) {
							continue;
						} else if (sub.matches("^(\\(+)\\d+(\\(+)")) {
							// replace these literals with unnecessary parentheses
							// with themselves
							String temp = stripUnnecessaryParentheses(sub);
							expr = expr.replaceAll(Pattern.quote(sub), temp);
							continue;
						}

						if (int_symbol_map.containsKey(sub)) {
							continue;
						} else {
							String temp = stripUnnecessaryParentheses(sub);
							if (int_symbol_map.containsKey(temp)) {
								String sym = int_symbol_map.get(temp);
								int_symbol_map.put(sub, sym);
							} else {
								String sym = "i" + int_symbol_map.size();
								int_symbol_map.put(sub, sym);
								if (!temp.equals(sub)) {
									// parentheses have been stripped off, also put the
									// stripped off version into the map
									int_symbol_map.put(temp, sym);
								}
							}
						}
					}

				}
			} else {
				if (e.matches("^true$") || e.matches("^false$")) {
					continue;
				} else if (e.matches("^(\\(+)true(\\)+)$")
						|| e.matches("^(\\(+)false(\\)+)$")) {
					// replace these literals with unnecessary parentheses with
					// themselves
					String temp = stripUnnecessaryParentheses(e);
					expr = expr.replaceAll(Pattern.quote(e), temp);
					continue;
				}

				// this subexpression can be variable names, function calls,
				// etc. Check if this subexpression has already had a symbol in
				// the map.
				if (bool_symbol_map.containsKey(e)) {
					continue;
				} else {
					String temp = stripUnnecessaryParentheses(e);
					if (bool_symbol_map.containsKey(temp)) {
						String sym = bool_symbol_map.get(temp);
						bool_symbol_map.put(e, sym);
					} else {
						String sym = "b" + bool_symbol_map.size();
						bool_symbol_map.put(e, sym);
						if (!temp.equals(e)) {
							// parentheses have been stripped off, also put the
							// stripped off version into the map
							bool_symbol_map.put(temp, sym);
						}
					}
				}
			}
		}

		// replace function calls and variable names with symbols
		// first check whether there are any variables named as i or b
		if (bool_symbol_map.keySet().contains("b")) {
			// check whether there are other symbolized expressions that also reference b
			HashMap<String, String> bool_update = new HashMap<String, String>();
			for (String name : bool_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", bool_symbol_map.get("b"));
					bool_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable b
			for (String name : bool_update.keySet()) {
				String value = bool_symbol_map.get(name);
				String new_name = bool_update.get(name);
				bool_symbol_map.put(new_name, value);
			}

			HashMap<String, String> int_update = new HashMap<String, String>();
			for (String name : int_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", bool_symbol_map.get("b"));
					int_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the integer map since it will be modified
			// by the symbolization of variable b
			for (String name : int_update.keySet()) {
				String value = int_symbol_map.get(name);
				String new_name = int_update.get(name);
				int_symbol_map.put(new_name, value);
			}

			// replace it first
			expr = expr.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", bool_symbol_map.get("b"));
		}

		if (bool_symbol_map.keySet().contains("i")) {
			// check whether there are other symbolized expressions that also reference b
			HashMap<String, String> bool_update = new HashMap<String, String>();
			for (String name : bool_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", bool_symbol_map.get("i"));
					bool_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable b
			for (String name : bool_update.keySet()) {
				String value = bool_symbol_map.get(name);
				String new_name = bool_update.get(name);
				bool_symbol_map.put(new_name, value);
			}

			HashMap<String, String> int_update = new HashMap<String, String>();
			for (String name : int_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", bool_symbol_map.get("i"));
					int_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the integer map since it will be modified
			// by the symbolization of variable b
			for (String name : int_update.keySet()) {
				String value = int_symbol_map.get(name);
				String new_name = int_update.get(name);
				int_symbol_map.put(new_name, value);
			}

			// replace it first
			expr = expr.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", bool_symbol_map.get("i"));
		}

		if (int_symbol_map.keySet().contains("i")) {
			// check whether there are other symbolized expressions that also reference i
			HashMap<String, String> bool_update = new HashMap<String, String>();
			for (String name : bool_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", int_symbol_map.get("i"));
					bool_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable i
			for (String name : bool_update.keySet()) {
				String value = bool_symbol_map.get(name);
				String new_name = bool_update.get(name);
				bool_symbol_map.put(new_name, value);
			}

			HashMap<String, String> int_update = new HashMap<String, String>();
			for (String name : int_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", int_symbol_map.get("i"));
					int_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable i
			for (String name : int_update.keySet()) {
				String value = int_symbol_map.get(name);
				String new_name = int_update.get(name);
				int_symbol_map.put(new_name, value);
			}

			// replace it first
			expr = expr.replaceAll("(?<![a-zA-Z0-9_])i(?![a-zA-Z0-9_])", int_symbol_map.get("i"));
		}

		if (int_symbol_map.keySet().contains("b")) {
			// check whether there are other symbolized expressions that also reference b
			HashMap<String, String> bool_update = new HashMap<String, String>();
			for (String name : bool_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", int_symbol_map.get("b"));
					bool_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable i
			for (String name : bool_update.keySet()) {
				String value = bool_symbol_map.get(name);
				String new_name = bool_update.get(name);
				bool_symbol_map.put(new_name, value);
			}

			HashMap<String, String> int_update = new HashMap<String, String>();
			for (String name : int_symbol_map.keySet()) {
				if (name.matches("^.*(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_]).*$")) {
					String new_name = name.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", int_symbol_map.get("b"));
					int_update.put(name, new_name);
				}
			}

			// update the symbolized expression in the boolean map since it will be modified
			// by the symbolization of variable i
			for (String name : int_update.keySet()) {
				String value = int_symbol_map.get(name);
				String new_name = int_update.get(name);
				int_symbol_map.put(new_name, value);
			}

			// replace it first
			expr = expr.replaceAll("(?<![a-zA-Z0-9_])b(?![a-zA-Z0-9_])", int_symbol_map.get("b"));
		}

		// then sort the keys first based on the length to avoid the case one key is
		// part of the other key
		Comparator<String> comparator = new Comparator<String>() {
			public int compare(String s1, String s2) {
				int diff = s1.length() - s2.length();
				if (diff > 0) {
					diff = -1;
				} else if (diff < 0) {
					diff = 1;
				}
				return diff;
			}
		};
		Set<String> ks = new HashSet<String>(bool_symbol_map.keySet());
		ks.addAll(int_symbol_map.keySet());
		// remove b and i (if any) because we have already symbolized them
		ks.remove("b");
		ks.remove("i");
		String[] sorted = ks.toArray(new String[0]);
		Arrays.sort(sorted, comparator);
		for (String s : sorted) {
			String sym;
			if (bool_symbol_map.containsKey(s)) {
				sym = bool_symbol_map.get(s);
			} else {
				sym = int_symbol_map.get(s);
			}
			expr = expr.replaceAll("(?<![a-zA-Z0-9_])" + Pattern.quote(s) + "(?![a-zA-Z0-9_])", sym);
		}

		return expr;
	}

	public String normalize(String expr) {
		expr = expr.replace("(rcv)", "rcv");

		// type erasure---get rid of '<' and '>' in parameterized types to avoid them
		// messing up splitting based on arithmetic operators
		expr = expr.replaceAll("<[a-zA-Z0-9\\?\\s]*>", "");

		// strip off string literals and the concatenation of strings
		expr = stripOffStringLiteralsAndStringConcatenations(expr);

		// replace null with 0 in the beginning so it won't make inconsistency of
		// symbolized tokens
		expr = expr.replace("null", "0");

		// replace ,) with ) because the boa ouput always appends one more comma after
		// the last argument
		expr = expr.replace(",)", ")");

		// replace !!! with !
		expr = expr.replace("!!!", "!");

		// replace API calls (if any) with symbols to avoid operators in arguments to
		// mess up the following tokenization
		expr = symbolizeAPICalls(expr);

		return expr;
	}

	private String symbolizeAPICalls(String expr) {
		char[] chars = expr.toCharArray();
		Matcher m = METHOD_CALL_START.matcher(expr);
		ArrayList<Point> ranges = new ArrayList<Point>();
		while (m.find()) {
			int start = m.start();
			int end = m.end();
			int paren = 1;
			// find where the API call ends
			for (int i = end; i < chars.length; i++) {
				char cur = chars[i];
				if (cur == '(') {
					paren++;
				} else if (cur == ')') {
					paren--;
					if (paren == 0) {
						// here is the close parenthesis
						end = i;
						break;
					}
				}
			}

			ranges.add(new Point(start, end));
		}

		// replace API calls with symbols
		String rel = "";
		int cur = 0;
		for (Point p : ranges) {
			if (p.x < cur) {
				// p is a sub-range of a previous range
				continue;
			} else {
				rel += expr.substring(cur, p.x);
				String apiCall = expr.substring(p.x, p.y + 1);
				if (call_symbol_map.containsKey(apiCall)) {
					rel += call_symbol_map.get(apiCall);
				} else {
					String sym = "m" + call_symbol_map.values().size();
					rel += sym;
					call_symbol_map.put(apiCall, sym);
				}
			}
			cur = p.y + 1;
		}

		if (cur <= expr.length()) {
			rel += expr.substring(cur);
		}

		return rel;
	}

	private String stripOffStringLiteralsAndStringConcatenations(String expr) {
		ArrayList<Point> ranges = new ArrayList<Point>();
		char[] chars = expr.toCharArray();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		int current_quote_starts = -1;
		for (int i = 0; i < chars.length; i++) {
			char cur = chars[i];
			if (cur == '"' && i > 0 && chars[i - 1] == '\\') {
				// count the number of backslashes
				int count = 0;
				while (i - count - 1 >= 0) {
					if (chars[i - count - 1] == '\\') {
						count++;
					} else {
						break;
					}
				}
				if (count % 2 == 0) {
					// escape one or more backslashes instead of this quote, end of quote
					// double quote ends
					inDoubleQuote = false;
				} else {
					// escape quote, not the end of the quote
				}
			} else if (cur == '"' && !inSingleQuote && !inDoubleQuote) {
				// double quote starts
				current_quote_starts = i;
				inDoubleQuote = true;
			} else if (cur == '\'' && !inSingleQuote && !inDoubleQuote) {
				// single quote starts
				current_quote_starts = i;
				inSingleQuote = true;
			} else if (cur == '\'' && i > 0 && chars[i - 1] == '\\') {
				// count the number of backslashes
				int count = 0;
				while (i - count - 1 >= 0) {
					if (chars[i - count - 1] == '\\') {
						count++;
					} else {
						break;
					}
				}
				if (count % 2 == 0) {
					// escape one or more backslashes instead of this quote, end of quote
					// single quote ends
					ranges.add(new Point(current_quote_starts, i));
					inSingleQuote = false;
					// reset
					current_quote_starts = -1;
				} else {
					// escape single quote, not the end of the quote
				}
			} else if (cur == '"' && !inSingleQuote && inDoubleQuote) {
				// double quote ends
				// add ranges of the quoted string to the list
				ranges.add(new Point(current_quote_starts, i));
				// reset
				inDoubleQuote = false;
				current_quote_starts = -1;
			} else if (cur == '\'' && inSingleQuote && !inDoubleQuote) {
				// single quote ends
				// add ranges of the quoted string to the list
				ranges.add(new Point(current_quote_starts, i));
				// reset
				inSingleQuote = false;
				current_quote_starts = -1;
			} else if (cur == '+' && !inSingleQuote && !inDoubleQuote) {
				boolean isStringConcatenation = false;
				// look backward to check if it is string concatenation
				int backward = i - 1;
				while (backward >= 0) {
					char c = chars[backward];
					if (c == ' ') {
						// continue to look backward
						backward--;
					} else if (c == '"') {
						// quote ends
						isStringConcatenation = true;
						break;
					} else {
						// not a string concatenation
						break;
					}
				}

				// look forward to check if it is string concatenation
				int forward = i + 1;
				while (forward < chars.length) {
					char c = chars[forward];
					if (c == ' ') {
						// continue to look backward
						forward++;
					} else if (c == '"') {
						// quote starts
						isStringConcatenation = true;
						break;
					} else {
						// not a string concatenation
						break;
					}
				}

				if (isStringConcatenation) {
					// remove this + operator
					ranges.add(new Point(i, i));
				}
			}
		}

		// remove whatever in the range list
		String rel = "";
		int cur = 0;
		for (Point p : ranges) {
			rel += expr.substring(cur, p.x);
			String rest = expr.substring(p.y + 1).trim();
			if (rel.trim().endsWith("==") || rel.trim().endsWith("!=") || rel.trim().endsWith(">=")
					|| rel.trim().endsWith("<=")) {
				// string comparison, replace the string with integer
				rel += "1";
			} else if (rest.startsWith("==") || rest.startsWith("!=") || rest.trim().startsWith(">=")
					|| rest.trim().startsWith("<=")) {
				rel += "1";
			}
			cur = p.y + 1;
		}

		if (cur <= expr.length()) {
			rel += expr.substring(cur);
		}

		return rel;
	}

	/**
	 * 
	 * Strip off unnecessary parentheses, we assume that the number of open
	 * parentheses and close parentheses are the same
	 * 
	 * @param expr
	 * @return
	 */
	protected String stripUnnecessaryParentheses(String expr) {
		String rel = expr;

		while (rel.startsWith("(") && rel.endsWith(")")) {
			rel = rel.substring(1, rel.length() - 1);
		}

		return rel;
	}

	public static String stripUnbalancedParentheses(String expr) {
		String rel = expr;
		char[] chars = expr.toCharArray();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		int left = 0;
		int right = 0;
		for (int i = 0; i < chars.length; i++) {
			char cur = chars[i];
			if (cur == '"' && i > 0 && chars[i - 1] == '\\') {
				// count the number of backslashes
				int count = 0;
				while (i - count - 1 >= 0) {
					if (chars[i - count - 1] == '\\') {
						count++;
					} else {
						break;
					}
				}
				if (count % 2 == 0) {
					// escape one or more backslashes instead of this quote, end of quote
					// double quote ends
					inDoubleQuote = false;
				} else {
					// escape quote, not the end of the quote
				}
			} else if (cur == '"' && !inSingleQuote && !inDoubleQuote) {
				// double quote starts
				inDoubleQuote = true;
			} else if (cur == '\'' && i > 0 && chars[i - 1] == '\\') {
				// count the number of backslashes
				int count = 0;
				while (i - count - 1 >= 0) {
					if (chars[i - count - 1] == '\\') {
						count++;
					} else {
						break;
					}
				}
				if (count % 2 == 0) {
					// escape one or more backslashes instead of this quote, end of quote
					// single quote ends
					inSingleQuote = false;
				} else {
					// escape single quote, not the end of the quote
				}
			} else if (cur == '\'' && !inSingleQuote && !inDoubleQuote) {
				// single quote starts
				inSingleQuote = true;
			} else if (cur == '"' && inDoubleQuote && !inSingleQuote) {
				// double quote ends
				inDoubleQuote = false;
			} else if (cur == '\'' && inSingleQuote && !inDoubleQuote) {
				// single quote ends
				inSingleQuote = false;
			} else if (inSingleQuote || inDoubleQuote) {
				// ignore any separator in quote

			} else if (cur == '(') {
				left++;
			} else if (cur == ')') {
				right++;
			}
		}

		// remove extra parentheses in each clause
		if (left > right && rel.startsWith("(")) {
			for (int i = 0; i < left - right; i++) {
				if (rel.startsWith("(")) {
					rel = rel.substring(1);
				}
			}
		} else if (left < right && rel.endsWith(")")) {
			for (int i = 0; i < right - left; i++) {
				if (rel.endsWith(")")) {
					rel = rel.substring(0, rel.length() - 1);
				}
			}
		}

		return rel;
	}
}
