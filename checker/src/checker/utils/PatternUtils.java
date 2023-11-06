package checker.utils;

import java.util.ArrayList;
import checker.model.APICall;
import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.ControlConstruct;

public class PatternUtils {
	public static ArrayList<APISeqItem> convert(String pattern) {
		String[] strSeqItems = pattern.split(", ");
		ArrayList<APISeqItem> patternArray = new ArrayList<APISeqItem>();
		for (String strItem : strSeqItems) {
			// instantiate either a ControlConstruct or
			// an APICallItem and add to currentPattern
			strItem = strItem.trim();
			switch (strItem) {
				case "TRY":
					patternArray.add(ControlConstruct.TRY);
					break;
				case "FINALLY":
					patternArray.add(ControlConstruct.FINALLY);
					break;
				case "IF":
					patternArray.add(ControlConstruct.IF);
					break;
				case "ELSE":
					patternArray.add(ControlConstruct.ELSE);
					break;
				case "LOOP":
					patternArray.add(ControlConstruct.LOOP);
					break;
				case "END_BLOCK":
					patternArray.add(ControlConstruct.END_BLOCK);
					break;
				default:
					if (strItem.startsWith("CATCH(") && strItem.endsWith(")")) {
						CATCH catchClause = new CATCH(
								strItem.substring(strItem.indexOf('(') + 1, strItem.indexOf(')')));
						patternArray.add(catchClause);
					} else {
						String name = strItem.substring(0, strItem.indexOf('('));
						String guard = strItem.substring(strItem.indexOf('@') + 1);
						String args = strItem.substring(strItem.indexOf('(') + 1, strItem.indexOf(')'));
						ArrayList<String> argList = new ArrayList<String>();
						for (String arg : args.split(",")) {
							if (!arg.trim().isEmpty()) {
								argList.add(arg);
							}
						}
						patternArray.add(new APICall(name, guard, null, null, argList));
					}
			}
		}

		return patternArray;
	}
}
