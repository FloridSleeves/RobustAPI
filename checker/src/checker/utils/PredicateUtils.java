package checker.utils;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;

public class PredicateUtils {
	public static String condition(Set<String> vars, String predicate) {
		// replace bitwise or with logical or
		// predicate =
		// predicate.replaceAll("(?<!\\||\\d\\s|\\d)\\|(?!(\\||\\s\\d|\\d|(\\s)*[a-zA-Z0-9_]+\\)(\\s)*(\\!=|==|<=|<|>|>=)))",
		// "||");
		// replace bitwise and with logical and
		// predicate =
		// predicate.replaceAll("(?<!&|\\d\\s|\\d)&(?!(&|\\s\\d|\\d|(\\s)*[a-zA-Z0-9_]+\\)(\\s)*(\\!=|==|<=|<|>|>=)))",
		// "&&");

		if (predicate.contains("?")) {
			return "conditional";
		}

		if (predicate.contains(">>") || predicate.contains("<<")) {
			return "bitshift";
		}

		if (predicate.matches("^.*(?<!\\|)\\|(?!\\|).*$") || predicate.matches("^.*(?<!&)&(?!&).*$")) {
			return "bitwise";
		}

		// normalize the use of assignment in the middle of a predicate as the assigned
		// variable
		predicate = replaceAssignment(predicate);

		String[] arr = splitOutOfQuote(predicate);
		String res = predicate;
		for (String c : arr) {
			c = c.trim();
			if (c.isEmpty() || c.equals("(") || c.equals(")")) {
				continue;
			} else {
				c = SAT.stripUnbalancedParentheses(c);

				boolean flag = false;
				for (String var : vars) {
					if (containsVar(var, c, 0)) {
						flag = true;
					}
				}

				if (!flag) {
					// this clause is irrelevant
					res = conditionClause(c, res);
				}
			}
		}

		// a && !b | a ==> a && !true, which is always evaluated to false
		// Such conditioning is incomplete because !b should be replaced with true
		// instead of b
		// So we add the following replacement statement to replace !true with true
		// we also need to handle cases such as !(true && true), !(true || true), !(true
		// || true && true), etc.
		while (res.matches("^.*true(\\s)*&&(\\s)*true.*$") || res.matches("^.*true(\\s)*\\|\\|(\\s)*true.*$")
				|| res.matches("^.*\\!true.*$") || res.matches("^.*\\!\\(true\\).*$")) {
			if (res.matches("^.*true(\\s)*&&(\\s)*true.*$")) {
				res = res.replaceAll("true(\\s)*&&(\\s)*true", "true");
			} else if (res.matches("^.*true(\\s)*\\|\\|(\\s)*true.*$")) {
				res = res.replaceAll("true(\\s)*\\|\\|(\\s)*true", "true");
			} else if (res.matches("^.*\\!true.*$")) {
				res = res.replaceAll("\\!true", "true");
			} else {
				res = res.replaceAll("\\!\\(true\\)", "true");
			}
		}

		return res;
	}

	public static String replaceAssignment(String predicate) {
		if (predicate.matches("^.+(?<!(=|\\!|>|<))=(?!=).+$")) {
			// this algorithm is based on one observation that an assignment sub-expression
			// must be wrapped with parentheses in a boolean expression
			char[] chars = predicate.toCharArray();
			Stack<Integer> stack = new Stack<Integer>();
			int snapshot = -1;
			int assignment_index = -1;
			boolean inSingleQuote = false;
			boolean inDoubleQuote = false;
			ArrayList<Point> ranges = new ArrayList<Point>();
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
				} else if (cur == '"' && !inSingleQuote && inDoubleQuote) {
					// double quote ends
					inDoubleQuote = false;
				} else if (cur == '\'' && inSingleQuote && !inDoubleQuote) {
					// single quote ends
					inSingleQuote = false;
				} else if (inSingleQuote || inDoubleQuote) {
					// ignore any separator in quote
				} else if (cur == '=') {
					if (i + 1 < chars.length && chars[i + 1] == '=') {
						// equal operator, ignore
						i++;
					} else if (i - 1 >= 0 && (chars[i - 1] == '!' || chars[i - 1] == '<' || chars[i - 1] == '>')) {
						// not equal operator, ignore
					} else {
						// assignment operator, stack size must be at least 1
						snapshot = stack.size();
						assignment_index = i;
					}
				} else if (cur == '(') {
					stack.push(i);
				} else if (cur == ')') {
					stack.pop();
					if (stack.size() == snapshot - 1) {
						ranges.add(new Point(assignment_index, i));
						// reset
						snapshot = -1;
						assignment_index = -1;
					}
				}
			}

			// remove whatever in the range list
			String rel = "";
			int cur = 0;
			for (Point p : ranges) {
				rel += predicate.substring(cur, p.x);
				cur = p.y;
			}

			if (cur <= predicate.length()) {
				rel += predicate.substring(cur);
			}

			return rel;
		} else {
			return predicate;
		}
	}

	public static String[] splitOutOfQuote(String s) {
		ArrayList<String> tokens = new ArrayList<String>();
		char[] chars = s.toCharArray();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		int inArgList = 0;
		StringBuilder sb = new StringBuilder();
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
					sb.append(cur);
				} else {
					// escape quote, not the end of the quote
					sb.append(cur);
				}
			} else if (cur == '"' && !inSingleQuote && !inDoubleQuote) {
				// double quote starts
				inDoubleQuote = true;
				sb.append(cur);
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
					sb.append(cur);
				} else {
					// escape single quote, not the end of the quote
					sb.append(cur);
				}
			} else if (cur == '\'' && !inDoubleQuote && !inSingleQuote) {
				// single quote starts
				inSingleQuote = true;
				sb.append(cur);
			} else if (cur == '"' && !inSingleQuote && inDoubleQuote) {
				// quote ends
				inDoubleQuote = false;
				sb.append(cur);
			} else if (cur == '\'' && inSingleQuote && !inDoubleQuote) {
				// single quote ends
				inSingleQuote = false;
				sb.append(cur);
			} else if (inArgList == 0 && cur == '(' && !inSingleQuote && !inDoubleQuote) {
				// look behind to check if this is a method call
				int behind = i - 1;
				while (behind >= 0) {
					if (chars[behind] == ' ') {
						// continue to look behind
						behind = behind - 1;
					} else if ((chars[behind] >= 'a' && chars[behind] <= 'z') ||
							(chars[behind] >= 'A' && chars[behind] <= 'Z') ||
							(chars[behind] >= '0' && chars[behind] <= '9') ||
							chars[behind] == '_') {
						// this is a method call
						inArgList++;
						break;
					} else {
						// not a method call
						break;
					}
				}
				sb.append(cur);
			} else if (inArgList > 0 && cur == '(' && !inSingleQuote && !inDoubleQuote) {
				// already in an argument list. Since we cannot easily identify the end of
				// argument list
				// due to the formatting inconsistency between partial program analysis and BOA
				// query, I have
				// to count every parenthesis in the argument list until it is 0
				inArgList++;
				sb.append(cur);
			} else if (inArgList > 0 && cur == ')' && !inSingleQuote && !inDoubleQuote) {
				inArgList--;
				sb.append(cur);
			} else if (inSingleQuote || inArgList > 0 || inDoubleQuote) {
				// ignore any separator in quote or in a method call
				sb.append(cur);
			} else if (cur == '&' || cur == '|') {
				// look ahead
				if (i + 1 < chars.length && chars[i + 1] == cur) {
					// step forward if it is logic operator, otherwise it is a bitwise operator
					i++;
					if (sb.length() > 0) {
						// push previous concatenated chars to the array
						tokens.add(sb.toString());
						// clear the string builder
						sb.setLength(0);
					}
				} else {
					// bitwise separator
					sb.append(cur);
				}
			} else if (cur == '!') {
				// look ahead
				if (i + 1 < chars.length && chars[i + 1] == '=') {
					// != operator instead of logic negation operator
					sb.append(cur);
				} else {
					if (sb.length() > 0) {
						// push previous concatenated chars to the array
						tokens.add(sb.toString());
						// clear the string builder
						sb.setLength(0);
					}
				}
			} else {
				sb.append(cur);
			}
		}

		// push the last token if any
		if (sb.length() > 0) {
			tokens.add(sb.toString());
		}

		String[] arr = new String[tokens.size()];
		for (int i = 0; i < tokens.size(); i++) {
			arr[i] = tokens.get(i);
		}
		return arr;
	}

	public static String normalize(String predicate, ArrayList<String> rcv_candidates,
			ArrayList<ArrayList<String>> args_candidates) {
		String norm = predicate;
		for (String rcv : rcv_candidates) {
			if (norm.contains(rcv)) {
				// cannot simply call replaceAll since some name be appear as part of other
				// names
				// norm = norm.replaceAll(Pattern.quote(rcv), "rcv");
				norm = replaceVar(rcv, norm, 0, "rcv");
			}
		}

		for (ArrayList<String> args : args_candidates) {
			for (int i = 0; i < args.size(); i++) {
				if (norm.contains(args.get(i))) {
					// cannot simply call replaceAll since some name be appear as part of other
					// names
					// norm = norm.replaceAll(Pattern.quote(args.get(i)), "arg" + i);
					norm = replaceVar(args.get(i), norm, 0, "arg" + i);
				}
			}
		}

		return norm.trim();
	}

	public static boolean containsVar(String var, String clause, int start) {
		if (clause.substring(start).contains(var)) {
			boolean flag1 = false;
			boolean flag2 = false;
			// a small trick to avoid the case where a condition variable name is part of a
			// variable name in the clause
			int index = clause.indexOf(var, start);
			int ahead = index - 1;
			int behind = index + var.length();
			if (ahead >= 0 && ahead < clause.length()) {
				char c = clause.charAt(ahead);
				if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
					// something contains the variable name as part of it
					flag1 = false;
				} else {
					flag1 = true;
				}
			} else {
				flag1 = true;
			}

			if (behind >= 0 && behind < clause.length()) {
				char c = clause.charAt(behind);
				if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
					// something contains the variable name as part of it
					flag2 = false;
				} else {
					flag2 = true;
				}
			} else {
				flag2 = true;
			}

			if (flag1 && flag2 && !isInQuote(clause, index)) {
				return true;
			} else {
				// keep looking forward
				if (behind < clause.length()) {
					try {
						return containsVar(var, clause, behind);
					} catch (StackOverflowError err) {
						err.printStackTrace();
					}

					return false;
				} else {
					return false;
				}
			}
		} else {
			return false;
		}
	}

	public static String conditionClause(String clause, String predicate) {
		String res = predicate;
		String pre = "";
		// a small trick to check whether the part that matches the clause is a
		// stand-alone clause
		while (true) {
			if (res.indexOf(clause) == -1) {
				// the clause does not exist
				break;
			} else {
				boolean flag1 = false;
				boolean flag2 = false;

				// look ahead and see if it reaches a clause separator, e.g., &(&), |(|), !(!=)
				int ahead = res.indexOf(clause) - 1;
				while (ahead >= 0 && ahead < res.length()) {
					char c = res.charAt(ahead);
					if (c == ' ' || c == '(') {
						// okay lets keep looking back
						ahead--;
					} else if (c == '&' || c == '!' || c == '|') {
						flag1 = true;
						break;
					} else {
						break;
					}
				}

				if (ahead == -1) {
					// this clause appear in the beginning
					flag1 = true;
				}

				int behind = res.indexOf(clause) + clause.length();
				while (behind >= 0 && behind < res.length()) {
					char c = res.charAt(behind);
					if (c == ' ' || c == ')') {
						// okay lets keep looking behind
						behind++;
					} else if (c == '&' || c == '|') {
						flag2 = true;
						break;
					} else if (c == '!' && behind + 1 < res.length() && res.charAt(behind + 1) != '=') {
						flag2 = true;
						break;
					} else {
						break;
					}
				}

				if (behind == res.length()) {
					// this clause appears in the end
					flag2 = true;
				}

				if (flag1 && flag2) {
					// stand-alone clause
					String sub1 = res.substring(0, res.indexOf(clause));
					String sub2 = res.substring(res.indexOf(clause) + clause.length());
					return pre + sub1 + "true" + sub2;
				} else {
					// keep searching
					pre = res.substring(0, behind);
					res = res.substring(behind);
				}
			}
		}

		return predicate;
	}

	public static String replaceVar(String var, String predicate, int start, String substitute) {
		if (!containsVar(var, predicate, start)) {
			return predicate;
		}

		boolean flag1 = false;
		boolean flag2 = false;
		// a small trick to avoid the case where a condition variable name is part of a
		// variable name in the clause
		int index = predicate.indexOf(var, start);
		int ahead = index - 1;
		int behind = index + var.length();

		if (ahead >= 0 && ahead < predicate.length()) {
			char c = predicate.charAt(ahead);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
				// something contains the variable name as part of it
				flag1 = false;
			} else {
				flag1 = true;
			}
		} else {
			flag1 = true;
		}

		if (behind >= 0 && behind < predicate.length()) {
			char c = predicate.charAt(behind);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
				// something contains the variable name as part of it
				flag2 = false;
			} else {
				flag2 = true;
			}
		} else {
			flag2 = true;
		}

		String sub1 = predicate.substring(0, ahead + 1);
		String sub2 = predicate.substring(behind);
		if (flag1 && flag2 && !isInQuote(predicate, index)) {
			// replace it
			String predicate2 = sub1 + substitute + sub2;
			// recalculate behind index after substitution
			behind = behind + substitute.length() - var.length();
			return replaceVar(var, predicate2, behind, substitute);
		} else {
			// keep looking forward
			return replaceVar(var, predicate, behind, substitute);
		}
	}

	public static boolean isInQuote(String s, int index) {
		if (s.contains("\"") || s.contains("'")) {
			char[] chars = s.toCharArray();
			boolean inSingleQuote = false;
			boolean inDoubleQuote = false;
			for (int i = 0; i < chars.length; i++) {
				if (i == index) {
					return inSingleQuote || inDoubleQuote;
				}
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
				} else if (cur == '"' && !inSingleQuote && inDoubleQuote) {
					// double quote ends
					inDoubleQuote = false;
				} else if (cur == '\'' && inSingleQuote && !inDoubleQuote) {
					// single quote ends
					inSingleQuote = false;
				}
			}

			return inSingleQuote;
		} else {
			return false;
		}
	}
}
