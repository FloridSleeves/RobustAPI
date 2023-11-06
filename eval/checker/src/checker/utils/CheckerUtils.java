package checker.utils;

import java.util.ArrayList;

public class CheckerUtils {
	public static boolean isSubsequence(ArrayList<String> seq1, ArrayList<String> seq2) {
		int pos1 = 0;
		int pos2 = 0;
		for (; pos1 < seq1.size() && pos2 < seq2.size();) {
			if (seq1.get(pos1).equals(seq2.get(pos2))) {
				pos2++;
			}
			pos1++;
		}

		return pos2 == seq2.size();
	}
}
