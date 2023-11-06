package checker.model;

import java.util.ArrayList;
import java.util.regex.Matcher;

import checker.model.Pattern;
import checker.utils.PatternUtils;

public class Violation {
    public ViolationType type;
    public APISeqItem item;
    public String id;
    public String fix = "";
    public Pattern vioPattern;
    public ArrayList<Violation> redundancies;

    public Violation(ViolationType type, APISeqItem item) {
        this.type = type;
        this.item = item;
        this.redundancies = new ArrayList<Violation>();
    }

    @Override
    public boolean equals(Object o) {
        boolean isEqual = false;

        if (o instanceof Violation) {
            if (this.fix.equals(((Violation) o).fix)) {
                isEqual = true;
            }
        }

        return isEqual;
    }

    public void addRedundant(Violation other) {
        redundancies.add(other);
    }

    public boolean hasRedundancies() {
        return !redundancies.isEmpty();
    }

    public String getViolationMessage(Pattern _vioPattern) {
        vioPattern = _vioPattern;
        String message = "You may want to ";
        message += getTypeMessage();
        if (hasRedundancies()) {
            for (Violation v : redundancies) {
                v.vioPattern = _vioPattern;
                message += "You may also want to " + v.getTypeMessage();
            }
        }

        message += vioPattern.support + " Github code examples also do this.\n"
                + vioPattern.description;
        return message;
    }

    private String getTypeMessage() {
        String message = null;
        switch (type) {
            case IncorrectPrecondition:
                try {
                    message = "check whether " + findGuardCondition() + ". ";
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case MissingMethodCall:
                message = "call " + ((APICall) item).getName() + "() " +
                        missingAPIBeforeFocal() + " calling " + vioPattern.methodName + "(). ";
                break;
            case MissingStructure:
                message = getControlStructureType() + " here. ";
                break;
            case DisorderMethodCall:
                // TODO: make sure this is right
                message = "call " + ((APICall) item).getName() + "() " +
                        missingAPIBeforeFocal() + " calling " + vioPattern.methodName + "(). ";
                break;
            case DisorderStructure:
                // TODO: find out what order
                message = "call " + getControlStructureType() + " in a different order. ";
                break;
        }
        return message;
    }

    private String getControlStructureType() {
        String structure;
        if (item instanceof CATCH) {
            structure = "handle the potential " + ((CATCH) item).type
                    + " thrown by " + vioPattern.methodName + "() by using a try-catch block";
        } else if (item == ControlConstruct.FINALLY) {
            structure = "call " + vioPattern.methodName + "() in a finally block to ensure it is "
                    + "always called at the end in case of potential exceptions";
        } else if (item == ControlConstruct.IF) {
            // TODO: find guard condition
            structure = "use an if-statement";
        }
        // I would like to use an else violation for if-else clauses
        // to keep them distinct from solo if-statements
        else if (item == ControlConstruct.ELSE) {
            // TODO: find guard condition
            structure = "use an if-else statement";
        } else if (item == ControlConstruct.LOOP) {
            // TODO: find guard condition
            structure = "use a loop";
        } else {
            // I'm not sure what the default should be.
            structure = "use a control structure";
        }

        return structure;
    }

    private String missingAPIBeforeFocal() {
        // missing API call: ((APICall) item)
        // focal API call: vioPattern.className + vioPattern.methodName
        // find if missing API is before focal in vioPattern.pattern
        String ret;
        if (vioPattern.pattern.indexOf(((APICall) item).getName()) < vioPattern.pattern
                .indexOf(vioPattern.methodName)) {
            ret = "before";
        } else {
            ret = "after";
        }

        return ret;
    }

    private String findGuardCondition() throws Exception {
        String guard = null;
        String ret = "";
        ArrayList<APISeqItem> pArray = PatternUtils.convert(vioPattern.pattern);
        boolean isNeg = false;

        for (APISeqItem i : pArray) {
            if (i instanceof APICall && ((APICall) i).name.equals(((APICall) item).name)) {
                guard = ((APICall) i).condition;
                break;
            }
        }

        if (guard == null) {
            throw new Exception("Guard not found.");
        }

        if (guard.equals("true")) {
            // Tianyi: is it possible that for incorrect-precondition violations, the guard
            // in the pattern is true?
            ret = ((APICall) item).getName() + "() returns true";
        } else {
            if (guard.contains("rcv")) {
                // contexualize the receiver name
                guard = guard.replaceAll("rcv", ((APICall) item).receiver);
            }

            if (guard.charAt(0) == '!') {
                isNeg = true;
                // trim parenthesis off
                if (guard.charAt(1) == '(' && guard.charAt(guard.length() - 1) == ')') {
                    guard = guard.substring(2, guard.length() - 1);
                }
            }

            if (guard.contains("||")) {
                String[] g2 = guard.split("\\|\\|");

                for (int i = 0; i < g2.length; i++) {
                    ret += translateTokens(g2[i], isNeg);
                    if (i != g2.length - 1) {
                        ret += " or ";
                    }
                }
            } else {
                ret += translateTokens(guard, isNeg);
            }
        }

        return ret;
    }

    private String translateTokens(String tokenSeq, boolean isNeg) {
        String ret;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([><=!][=]?)");
        Matcher m = p.matcher(tokenSeq);

        if (m.find()) {
            // translate first token (substring from 0 to m.start())
            ret = translateSmallTokens(tokenSeq.substring(0, m.start()));
            // translate m.group() to natural language
            ret += " " + translateOp(m.group(), isNeg) + " ";
            // translate second token (substring from m.end()+1 to end)
            ret += translateSmallTokens(tokenSeq.substring(m.end()));
        } else {
            // just print as-is
            if (isNeg) {
                ret = tokenSeq + " returns false";
            } else {
                ret = tokenSeq + " returns true";
            }
        }

        return ret;
    }

    private String translateSmallTokens(String tok) {
        String ret;
        // tok is either null, arg*, rcv, or rcv.*
        if (tok.equals("null")) {
            ret = "null";
        } else {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(arg)[\\d]");
            Matcher m = p.matcher(tok);

            if (m.find()) {
                // tok is arg*
                switch (tok.charAt(tok.length() - 1)) {
                    case '0':
                        ret = "the first parameter";
                        break;
                    case '1':
                        ret = "the second parameter";
                        break;
                    case '2':
                        ret = "the third parameter";
                        break;
                    case '3':
                        ret = "the fourth parameter";
                        break;
                    case '4':
                        ret = "the fifth parameter";
                        break;
                    case '5':
                        ret = "the sixth parameter";
                        break;
                    case '6':
                        ret = "the seventh parameter";
                        break;
                    case '7':
                        ret = "the eighth parameter";
                        break;
                    case '8':
                        ret = "the ninth parameter";
                        break;
                    case '9':
                        ret = "the tenth parameter";
                        break;
                    default:
                        ret = "one of the parameters";
                }
            } else {
                // tok is rcv or rcv.*
                if (tok.contains(".")) {
                    // tok is rcv.*
                    ret = tok;
                } else {
                    // tok is rcv
                    ret = "the receiver of " + ((APICall) item).getName() + "()";
                }
            }
        }

        return ret;
    }

    private String translateOp(String op, boolean isNeg) {
        String ret = "is ";

        if (isNeg) {
            ret += "not ";
        }

        switch (op) {
            case "==":
                ret += "equal to";
                break;
            case "!=":
                ret += "not equal to";
                break;
            case ">":
                ret += "greater than";
                break;
            case ">=":
                ret += "greater than or equal to";
                break;
            case "<":
                ret += "less than";
                break;
            case "<=":
                ret += "less than or equal to";
                break;
            default:
                ret = "";
        }

        return ret;
    }
}
