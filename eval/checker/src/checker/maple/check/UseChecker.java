package checker.maple.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import checker.model.APICall;
import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.ControlConstruct;
import checker.model.Violation;
import checker.model.ViolationType;
import checker.utils.PredicateUtils;
import checker.utils.SAT;

public class UseChecker {

	private ArrayList<APISeqItem> common;

	// the violated pattern
	public ArrayList<APISeqItem> pattern;

	public UseChecker() {
		this.common = new ArrayList<APISeqItem>();
	}

	/**
	 * 
	 * Check a structured API call sequence against multiple reliable usage
	 * patterns. We consider the sequence reliable if it
	 * follows at least one usage pattern. If the sequence does not follow any
	 * patterns, characterize the violations.
	 * 1. Exception Handling
	 * 2. Error Handling
	 * 3. Weakest Precondition
	 * 4. API Call Ordering
	 * 5. API Call Completeness
	 * 
	 * @param pattern
	 * @param seq
	 * @return
	 */
	public ArrayList<Violation> validate(HashSet<ArrayList<APISeqItem>> patterns,
			ArrayList<APISeqItem> seq) {
		// first-round checking, find the one with the minimum sequence distance
		HashMap<ArrayList<APISeqItem>, ArrayList<APISeqItem>> closest_pattern_lcs = new HashMap<ArrayList<APISeqItem>, ArrayList<APISeqItem>>();
		HashMap<ArrayList<APISeqItem>, ArrayList<APISeqItem>> closest_pattern_common = new HashMap<ArrayList<APISeqItem>, ArrayList<APISeqItem>>();
		int diff = Integer.MAX_VALUE;
		for (ArrayList<APISeqItem> pattern : patterns) {
			// compute the longest common sub-sequence
			ArrayList<APISeqItem> lcs = LCS(pattern, seq);

			// find the most similar pattern
			if (pattern.size() - lcs.size() < diff) {
				// a new minimum distance
				diff = pattern.size() - lcs.size();
				closest_pattern_lcs.clear();
				closest_pattern_common.clear();
				closest_pattern_lcs.put(pattern, lcs);
				ArrayList<APISeqItem> closest_common = new ArrayList<APISeqItem>();
				closest_common.addAll(common);
				closest_pattern_common.put(pattern, closest_common);
			} else if (pattern.size() - lcs.size() == diff) {
				// same minimum distance
				closest_pattern_lcs.put(pattern, lcs);
				ArrayList<APISeqItem> closest_common = new ArrayList<APISeqItem>();
				closest_common.addAll(common);
				closest_pattern_common.put(pattern, closest_common);
			}

			// reset
			common.clear();
		}

		// second-round checking, also consider the precondition violations
		int num = Integer.MAX_VALUE;
		ArrayList<Violation> min_vio = null;
		for (ArrayList<APISeqItem> pattern : closest_pattern_lcs.keySet()) {
			ArrayList<Violation> violations = compute(pattern, seq, closest_pattern_lcs.get(pattern),
					closest_pattern_common.get(pattern));
			if (violations.size() < num) {
				num = violations.size();
				min_vio = violations;
				this.pattern = pattern;
			}
		}

		return min_vio;
	}

	public ArrayList<Violation> compute(ArrayList<APISeqItem> pattern, ArrayList<APISeqItem> seq,
			ArrayList<APISeqItem> lcs, ArrayList<APISeqItem> common) {
		ArrayList<Violation> violations = new ArrayList<Violation>();

		// compute the violation
		for (APISeqItem item : pattern) {
			if (item instanceof ControlConstruct || item instanceof CATCH) {
				if (!lcs.contains(item) && !seq.contains(item)) {
					violations.add(new Violation(ViolationType.MissingStructure, item));
					continue;
				} else if (!lcs.contains(item) && seq.contains(item)) {
					violations.add(new Violation(ViolationType.DisorderStructure, item));
				}
			} else {
				APICall call1 = (APICall) item;
				APICall call2 = null;
				// find the corresponding call2, if any
				if (lcs.contains(call1)) {
					int index = lcs.indexOf(call1);
					call2 = (APICall) common.get(index);
				} else {
					// try to find in the sequence
					for (APISeqItem a : seq) {
						if (a instanceof APICall && ((APICall) a).name.equals(call1.name)) {
							call2 = (APICall) a;
							violations.add(new Violation(ViolationType.DisorderMethodCall, call2));
							break;
						}
					}
				}

				if (call2 == null) {
					violations.add(new Violation(ViolationType.MissingMethodCall, item));
				} else {
					if (checkPrecondition(call2, call1)) {
						// precondition violation
						Violation vio = new Violation(
								ViolationType.IncorrectPrecondition, call2);
						violations.add(vio);
					}
				}
			}
		}

		ArrayList<Violation> disorderAPICalls = new ArrayList<Violation>();
		for (Violation vio : violations) {
			if (vio.type == ViolationType.DisorderMethodCall) {
				disorderAPICalls.add(vio);
			}
		}

		if (disorderAPICalls.size() == 1 && lcs.contains(ControlConstruct.TRY)) {
			// if there is a single disordered method call and both the snippet and the
			// pattern contain try-catch blocks,
			// report missing try-catch blocks instead of disorder API call
			violations.remove(disorderAPICalls.get(0));
			violations.add(new Violation(ViolationType.MissingStructure, ControlConstruct.TRY));
		}

		return violations;
	}

	public boolean checkPrecondition(APICall call2, APICall call1) {
		// check whether the precondition of API call in pattern is implied by the
		// precondition of the API call in the sequence
		SAT sat = new SAT();
		// condition and normalize the path condition of api2
		HashSet<String> relevant_element = new HashSet<String>();
		ArrayList<String> rcv = new ArrayList<String>();
		ArrayList<ArrayList<String>> args = new ArrayList<ArrayList<String>>();
		if (call2.receiver != null) {
			relevant_element.add(call2.receiver);
			rcv.add(call2.receiver);
		}
		relevant_element.addAll(call2.arguments);
		args.add(call2.arguments);
		String cond;
		if (!call2.condition.equals("true")) {
			cond = PredicateUtils.condition(
					relevant_element, call2.condition);
			cond = PredicateUtils.normalize(cond, rcv, args);
		} else {
			cond = "true";
		}
		if (!sat.checkImplication(cond, call1.condition)) {
			// precondition violation
			return true;
		}
		return false;
	}

	public ArrayList<APISeqItem> LCS(ArrayList<APISeqItem> seq1, ArrayList<APISeqItem> seq2) {
		int[][] lengths = new int[seq1.size() + 1][seq2.size() + 1];

		for (int i = 0; i < seq1.size(); i++) {
			APISeqItem item1 = seq1.get(i);
			for (int j = 0; j < seq2.size(); j++) {
				APISeqItem item2 = seq2.get(j);
				if (item1.getClass().equals(item2.getClass())) {
					if (item1 instanceof APICall) {
						APICall call1 = (APICall) item1;
						APICall call2 = (APICall) item2;
						if (call1.name.equals(call2.name)) {
							lengths[i + 1][j + 1] = lengths[i][j] + 1;
						} else {
							lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
						}
					} else if (item1 instanceof CATCH) {
						CATCH catch1 = (CATCH) item1;
						CATCH catch2 = (CATCH) item2;
						if (catch1.type.equals("Exception") || catch2.type.equals("Exception")
								|| catch1.type.equals(catch2.type)) {
							lengths[i + 1][j + 1] = lengths[i][j] + 1;
						} else {
							lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
						}
					} else {
						ControlConstruct construct1 = (ControlConstruct) item1;
						ControlConstruct construct2 = (ControlConstruct) item2;
						if (construct1.equals(construct2)) {
							lengths[i + 1][j + 1] = lengths[i][j] + 1;
						} else {
							lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
						}
					}
				} else {
					lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
				}
			}
		}

		ArrayList<APISeqItem> lcs = new ArrayList<APISeqItem>();
		for (int x = seq1.size(), y = seq2.size(); x != 0 && y != 0;) {
			if (lengths[x][y] == lengths[x - 1][y])
				x--;
			else if (lengths[x][y] == lengths[x][y - 1])
				y--;
			else {
				lcs.add(seq1.get(x - 1));
				common.add(seq2.get(y - 1));
				x--;
				y--;
			}
		}

		return lcs;
	}
}
