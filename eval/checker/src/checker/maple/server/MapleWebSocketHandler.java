package checker.maple.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import checker.maple.check.UseChecker;
import checker.maple.fix.FixGenerator;
import checker.model.APICall;
import checker.model.APISeqItem;
import checker.model.CATCH;
import checker.model.CodeSnippet;
import checker.model.ControlConstruct;
import checker.model.Pattern;
import checker.model.Violation;
import checker.model.ViolationType;
import checker.utils.PatternUtils;

@WebSocket
public class MapleWebSocketHandler {
	Session session = null;
	boolean _isFirstMessage = true;

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		this.session = null;
		System.out.println("Close: statusCode=" + statusCode + ", reason="
				+ reason);
	}

	@OnWebSocketError
	public void onError(Throwable t) {
		System.out.println("Error: " + t.getMessage());
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
		System.out.println("Connect: "
				+ session.getRemoteAddress().getAddress());
	}

	@OnWebSocketMessage
	public void onMessage(String message) {
		/* TEST */
		// System.out.println("Message: " + message);

		ObjectMapper mapper = new ObjectMapper();
		HashMap<Pattern, ArrayList<Violation>> vioMap = new HashMap<Pattern, ArrayList<Violation>>();

		// the first message will always be the code from the HTML page
		if (_isFirstMessage) {
			_isFirstMessage = false;

			// parse the JSON objects out of the message
			CodeSnippet[] snippets = null;
			try {
				// Convert JSON string to array of CodeSnippets
				snippets = mapper.readValue(message, CodeSnippet[].class);

			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			vioMap = parseAndAnalyzeCodeSnippet(snippets);

			// create a JSON message composed of violation messages + ids
			String jsonMessage;
			HashMap<String, ArrayList<HashMap<String, Object>>> apiMap = new HashMap<String, ArrayList<HashMap<String, Object>>>();

			// sort the violations by the vote score of each pattern and the number of
			// supported GitHub examples
			ArrayList<Pattern> sortedPatterns = new ArrayList<Pattern>(vioMap.keySet());
			Collections.sort(sortedPatterns, new Comparator<Pattern>() {

				public int compare(Pattern p1, Pattern p2) {
					int vote1 = p1.vote - p1.downvote;
					int vote2 = p2.vote - p2.downvote;
					if (vote1 == vote2) {
						return p2.support - p1.support;
					} else {
						return vote2 - vote1;
					}
				}
			});

			for (Pattern p : sortedPatterns) {
				for (Violation v : vioMap.get(p)) {
					HashMap<String, Object> vMap = new HashMap<String, Object>();
					vMap.put("vioMessage", v.getViolationMessage(p));
					vMap.put("pExample", StringEscapeUtils.escapeHtml4(v.fix));
					vMap.put("pID", p.id);
					vMap.put("csID", v.id);
					vMap.put("pVote", p.vote);
					vMap.put("pDownvote", p.downvote);

					if (p.links != null) {
						String[] linkArray = p.links.split("\\\\");

						int count = 0;
						for (int i = 0; i < linkArray.length; i += 2) {
							if (i % 4 == 0) {
								count++;
								vMap.put("ex" + count, linkArray[i]);
							} else {
								vMap.put("ex" + count + "Method", linkArray[i]);
							}
						}
					}

					if (!apiMap.containsKey(p.methodName)) {
						apiMap.put(p.methodName, new ArrayList<HashMap<String, Object>>());
					}

					apiMap.get(p.methodName).add(vMap);
				}
			}

			// send the JSON message to the Chrome plugin
			try {
				jsonMessage = mapper.writeValueAsString(apiMap);
				System.out.println(jsonMessage);
				session.getRemote().sendString(jsonMessage);
			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// any following messages will be up/downvotes. We know which
			// pattern
			// is being voted on based on the vote's id, whose index corresponds
			// with the page of its pattern
			JsonNode voteMessage;
			try {
				voteMessage = mapper.readValue(message, JsonNode.class);
				/* TEST */
				if (voteMessage.get("vote").toString() != "null") {
					System.out.println(voteMessage.get("vote").toString());
					System.out.println("Vote:" + voteMessage.get("vote").asText()
							+ ", ID:" + voteMessage.get("id").asText());
					// DONE: send to MySQL
					MySQLAccess dbAccess = new MySQLAccess();
					dbAccess.connect();
					dbAccess.addVote(1, Integer.parseInt(voteMessage.get("id").asText()));
					dbAccess.close();
				}
				if (voteMessage.get("downvote").toString() != "null") {
					System.out.println("Downvote:" + voteMessage.get("downvote").asText()
							+ ", ID:" + voteMessage.get("id").asText());
					MySQLAccess dbAccess2 = new MySQLAccess();
					dbAccess2.connect();
					dbAccess2.addDownvote(1, Integer.parseInt(voteMessage.get("id").asText()));
					dbAccess2.close();
				}
			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private HashMap<Pattern, ArrayList<Violation>> parseAndAnalyzeCodeSnippet(CodeSnippet[] snippets) {
		HashMap<String, HashSet<Pattern>> patterns = new HashMap<String, HashSet<Pattern>>();
		HashMap<Pattern, ArrayList<Violation>> vioMap = new HashMap<Pattern, ArrayList<Violation>>();
		MySQLAccess dbAccess = new MySQLAccess();

		// only connect it once
		dbAccess.connect();

		// analyze the code snippets and return patterns to the plugin
		for (CodeSnippet cs : snippets) {
			try {
				// parse and analyze a snippet using Maple
				String snippet = cs.getSnippet();
				if (!snippet.contains(";") && !(snippet.split(System.lineSeparator()).length > 1)) {
					continue;
				}
				PartialProgramAnalyzer analyzer = new PartialProgramAnalyzer(
						snippet);
				HashSet<Pattern> ps;
				HashMap<String, ArrayList<APISeqItem>> seqs = analyzer
						.retrieveAPICallSequences();

				HashSet<String> checkedMethodCalls = new HashSet<String>();
				for (String method : seqs.keySet()) {
					// get the corresponding call sequence for a method
					// in the snippet
					ArrayList<APISeqItem> seq = seqs.get(method);

					for (APISeqItem item : seq) {
						if (!(item instanceof APICall)) {
							continue;
						}

						String name = ((APICall) item).getName();
						String type = ((APICall) item).receiver_type;
						if (checkedMethodCalls.contains(type + "." + name)) {
							continue;
						} else {
							checkedMethodCalls.add(type + "." + name);
						}

						if (type == null || type.equals("unresolved")) {
							// TODO: check against the oracle
							continue;
						}

						if (patterns.containsKey(name)) {
							// the pattern of this API method exists
							ps = patterns.get(name);
						} else {
							// get patterns from the database
							if (name.startsWith("new ")) {
								// remove the new keyword and the following whilespace
								ps = dbAccess.getPatterns(name.substring(4), type);
							} else {
								ps = dbAccess.getPatterns(name, type);
							}
							patterns.put(name, ps);
						}

						// organize the patterns
						HashSet<HashSet<ArrayList<APISeqItem>>> pset = new HashSet<HashSet<ArrayList<APISeqItem>>>();
						HashSet<ArrayList<APISeqItem>> alterPatterns = new HashSet<ArrayList<APISeqItem>>();
						for (Pattern p : ps) {
							ArrayList<APISeqItem> pArray = PatternUtils
									.convert(p.pattern);
							if (p.isRequired) {
								HashSet<ArrayList<APISeqItem>> newP = new HashSet<ArrayList<APISeqItem>>();
								newP.add(pArray);
								pset.add(newP);
							} else {
								alterPatterns.add(pArray);
							}
						}

						if (!alterPatterns.isEmpty()) {
							pset.add(alterPatterns);
						}

						// check for violation or alternative usage
						for (HashSet<ArrayList<APISeqItem>> s : pset) {
							UseChecker checker = new UseChecker();
							ArrayList<Violation> viosTemp = checker.validate(s, seq);
							Pattern vioPattern = null;
							for (Pattern p : ps) {
								ArrayList<APISeqItem> list = PatternUtils.convert(p.pattern);
								if (list.equals(checker.pattern)) {
									vioPattern = p;
									break;
								}
							}

							if (vioPattern == null) {
								continue;
							}

							// remove redundant violations
							ArrayList<Violation> vios = new ArrayList<Violation>();
							for (int i = 0; i < viosTemp.size(); i++) {
								if (viosTemp.get(i).item instanceof APICall
										// || viosTemp.get(i).item == ControlConstruct.TRY
										|| viosTemp.get(i).item instanceof CATCH
										|| viosTemp.get(i).item == ControlConstruct.FINALLY
										|| viosTemp.get(i).item == ControlConstruct.LOOP) {
									// if this is an APICall, save it
									// if this is a try-catch, save CATCH
									// if this is a try-catch-finally, save FINALLY
									// if this is a loop, save LOOP
									viosTemp.get(i).id = cs.getId();
									vios.add(viosTemp.get(i));
								} else if (viosTemp.get(i).item == ControlConstruct.IF) {
									// if this is a solo IF, save the IF
									// if this is part of an if-else, save the ELSE
									if ((i + 2 < viosTemp.size())
											&& viosTemp.get(i + 2).item == ControlConstruct.ELSE) {
										viosTemp.get(i + 2).id = cs.getId();
										vios.add(viosTemp.get(i + 2));
									} else {
										viosTemp.get(i).id = cs.getId();
										vios.add(viosTemp.get(i));
									}
								}
							}

							// if we have one missing-if-construct violation and also an
							// incorrect-precondition violation,
							// and if the if construct occurs before the focal API, only keep the
							// incorrect-precondition violation
							ArrayList<APISeqItem> pattern = PatternUtils.convert(vioPattern.pattern);
							int ifIndex = -1;
							int focalIndex = -1;
							for (int j = 0; j < pattern.size(); j++) {
								APISeqItem item2 = pattern.get(j);
								if (item2 instanceof APICall && ((APICall) item2).getName().equals(name)) {
									focalIndex = j;
								} else if (item instanceof ControlConstruct && item == ControlConstruct.IF) {
									ifIndex = j;
								}
							}

							boolean hasMissingIf = false;
							int missingIfIndex = -1;
							boolean hasIncorrectPrecondition = false;
							for (int j = 0; j < vios.size(); j++) {
								Violation v = vios.get(j);
								if (v.type == ViolationType.MissingStructure && v.item instanceof ControlConstruct
										&& v.item == ControlConstruct.IF) {
									hasMissingIf = true;
									missingIfIndex = j;
								} else if (v.type == ViolationType.IncorrectPrecondition) {
									hasIncorrectPrecondition = true;
								}
							}

							if (ifIndex < focalIndex && hasMissingIf && hasIncorrectPrecondition) {
								vios.remove(missingIfIndex);
							}

							// generate fix suggestions for the remaining violations
							FixGenerator fg = new FixGenerator();
							HashMap<String, Violation> fixes = new HashMap<String, Violation>();
							ArrayList<Violation> vioTemp = new ArrayList<Violation>();
							for (Violation v : vios) {
								String fix = fg.generate(pattern, seq, (APICall) item);
								v.fix = fix;
								if (fixes.containsKey(fix)) {
									Violation tmp = fixes.get(fix);
									tmp.addRedundant(v);
									vioTemp.remove(vioTemp.indexOf(tmp));
									vioTemp.add(tmp);
									fixes.replace(fix, tmp);
								} else {
									fixes.put(fix, v);
									vioTemp.add(v);
								}
							}

							if (vioMap.containsKey(vioPattern)) {
								ArrayList<Violation> existingVios = vioMap.get(vioPattern);
								existingVios.addAll(vioTemp);
								vioMap.put(vioPattern, existingVios);
							} else {
								vioMap.put(vioPattern, vioTemp);
							}
						}
					}
				}
			} catch (Exception e) {
				// do nothing if we get an exception when parsing and
				// analyzing the snippet
				continue;
			}
		}
		dbAccess.close();
		return vioMap;
	}
}
