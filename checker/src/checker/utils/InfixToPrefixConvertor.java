package checker.utils;

import java.util.ArrayList;
import java.util.Stack;

public class InfixToPrefixConvertor {
	public static void main(String[] args) {
		System.out.println(infixToPrefixConvert(
				"! ( ( ( 1 + 2 * 3 ) != 0 ) && a && b )"));
	}

	public static boolean isOperand(String s) {
		return !(s.equals("+") || s.equals("-") || s.equals("/")
				|| s.equals("*") || s.equals("(") || s.equals(")")
				|| s.equals("||") || s.equals("&&") || s.equals("!")
				|| s.equals(">") || s.equals("<") || s.equals(">=")
				|| s.equals("<=") || s.equals("!=") || s.equals("==")
				|| s.equals("%"));
	}

	public static String operationCombine(Stack<String> operatorStack,
			Stack<String> operandStack) {
		String operator = operatorStack.pop();
		String operand;
		if (operator.equals("!")) {
			// unary operator
			String unary_operand = operandStack.pop();
			operand = "(" + operator + " " + unary_operand + ")";
		} else if (operator.equals("!=")) {
			// convert (!= a b) to (! (== a b)) for ease of encoding prefix expression to
			// SMT-lib format
			String rightOperand = operandStack.pop();
			String leftOperand = operandStack.pop();
			operand = "(!" + " (== " + leftOperand + " " + rightOperand
					+ "))";
		} else {
			String rightOperand = operandStack.pop();
			String leftOperand = operandStack.pop();
			operand = "(" + operator + " " + leftOperand + " " + rightOperand
					+ ")";
		}

		return operand;
	}

	public static int rank(String s) {
		if (s.equals("!")) {
			return 6;
		} else if (s.equals(">") || s.equals("<") || s.equals(">=")
				|| s.equals("<=")) {
			return 3;
		} else if (s.equals("&&") || s.equals("||")) {
			return 1;
		} else if (s.equals("!=") || s.equals("==")) {
			return 2;
		} else if (s.equals("+") || s.equals("-")) {
			return 4;
		} else if (s.equals("/") || s.equals("*") || s.equals("%")) {
			return 5;
		} else {
			return 0;
		}
	}

	public static String infixToPrefixConvert(String infix) {
		Stack<String> operandStack = new Stack<String>();
		Stack<String> operatorStack = new Stack<String>();

		// StringTokenizer tokenizer = new StringTokenizer(infix);
		ArrayList<String> tokens = tokenize(infix);
		for (String token : tokens) {
			if (isOperand(token)) {
				operandStack.push(token);
			}

			else if (token.equals("(") || operatorStack.isEmpty()
					|| rank(token) > rank(operatorStack.peek())) {
				operatorStack.push(token);
			}

			else if (token.equals(")")) {
				while (!operatorStack.peek().equals("(")) {
					operandStack.push(operationCombine(operatorStack,
							operandStack));
				}
				operatorStack.pop();
			}

			else if (rank(token) <= rank(operatorStack.peek())) {
				while (!operatorStack.isEmpty()
						&& rank(token) <= rank(operatorStack.peek())) {
					operandStack.push(operationCombine(operatorStack,
							operandStack));
				}
				operatorStack.push(token);
			}
		}
		while (!operatorStack.isEmpty()) {
			operandStack.push(operationCombine(operatorStack, operandStack));
		}
		return (operandStack.peek());
	}

	public static ArrayList<String> tokenize(String expr) {
		ArrayList<String> tokens = new ArrayList<String>();
		char[] chs = expr.toCharArray();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < chs.length; i++) {
			char c = chs[i];
			if (c == '(' || c == ')' || c == '+' || c == '*'
					|| c == '/' || c == '%') {
				if (sb.length() > 0) {
					// push previous concatenated chars to the array
					tokens.add(sb.toString());
					// clear the string builder
					sb.setLength(0);
				}
				// add the operator into the array
				tokens.add(String.valueOf(c));
			} else if (c == '>' || c == '<' || c == '!' || c == '=') {
				if (sb.length() > 0) {
					// push previous concatenated chars to the array
					tokens.add(sb.toString());
					// clear the string builder
					sb.setLength(0);
				}
				// look ahead
				if (i + 1 < chs.length && chs[i + 1] == '=') {
					i++;
					sb.append(c);
					sb.append('=');
					tokens.add(sb.toString());
					sb.setLength(0);
				} else {
					tokens.add(String.valueOf(c));
				}
			} else if (c == '|' || c == '&') {
				if (sb.length() > 0) {
					// push previous concatenated chars to the array
					tokens.add(sb.toString());
					// clear the string builder
					sb.setLength(0);
				}
				// look ahead
				if (i + 1 < chs.length && chs[i + 1] == c) {
					i++;
					sb.append(c);
					sb.append(c);
					tokens.add(sb.toString());
					sb.setLength(0);
				} else {
					tokens.add(String.valueOf(c));
				}
			} else if (c == ' ') {
				// empty space
				continue;
			} else if (c == '-') {
				// check whether it is a negative sign or a minus sign
				boolean isNeg = false;

				int backward = i - 1;
				while (backward >= 0) {
					// look backward till the first character that is not empty space
					char b = chs[backward];
					if (b != ' ') {
						if (b == '(' || b == '=' || b == '*' || b == '/' || b == '+' || b == '-' || b == '<' || b == '>'
								|| b == '%') {
							isNeg = true;
						}
						break;
					}
					backward--;
				}

				if (backward == -1) {
					// reach the very beginning, this is a negative integer in the beginning of the
					// expression
					isNeg = true;
				}

				if (isNeg) {
					if (i + 1 < chs.length && chs[i + 1] == 'i') {
						// Z3 does not support negative sign in front of an integer variable, so we will
						// ditch the negative sign in front of integer variables
						continue;
					} else {
						sb.append(c);
					}
				} else {
					// minus sign
					if (sb.length() > 0) {
						// push previous concatenated chars to the array
						tokens.add(sb.toString());
						// clear the string builder
						sb.setLength(0);
					}
					// add the operator into the array
					tokens.add(String.valueOf(c));
				}
			} else {
				sb.append(c);
			}
		}

		// push the last token if any
		if (sb.length() > 0) {
			tokens.add(sb.toString());
		}

		return tokens;
	}
}
