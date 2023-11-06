package checker.maple.server;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.nio.file.Path;

public class Evaluator {
    static HashMap<String, HashMap<String, String>> patternList;

    public static void main(String[] args) throws Exception {
        // parse the JSON objects out of the message
        readFromFile(args[1]);
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        // read all lines of the input file
        String line, cs = "";
        while ((line = br.readLine()) != null) {
            cs += line + "\n";
        }
        // System.out.println("cs: " + cs);
        if (cs == null) {
            System.out.println("NOCODE: No code snippet provided");
            return;
        }
        CodeSnippet snippets = null;
        HashMap<Pattern, ArrayList<Violation>> vioMap = new HashMap<Pattern, ArrayList<Violation>>();
        // Convert JSON string to array of CodeSnippets
        snippets = new CodeSnippet();
        snippets.setSnippet(cs);
        snippets.setId("1");
        if (snippets == null) {
            System.out.println("NOCODE: No code snippet provided");
            return;
        }
        try {
            vioMap = parseAndAnalyzeCodeSnippet(snippets);
        } catch (Exception e) {
            System.out.println("PARSEFAIL: " + e.getMessage());
            e.printStackTrace(System.out);
            return;
        }
        if (vioMap == null)
            return;

        // create a JSON message composed of violation messages + ids
        String jsonMessage;
        HashMap<String, ArrayList<HashMap<String, Pattern>>> apiMap = new HashMap<String, ArrayList<HashMap<String, Pattern>>>();
        ArrayList<Pattern> sortedPatterns = new ArrayList<Pattern>(vioMap.keySet());

        for (Pattern p : sortedPatterns) {
            for (Violation v : vioMap.get(p)) {
                HashMap<String, Pattern> vMap = new HashMap<String, Pattern>();
                vMap.put("vioPattern", v.vioPattern);
                if (!apiMap.containsKey(p.methodName)) {
                    apiMap.put(p.methodName, new ArrayList<HashMap<String, Pattern>>());
                }

                apiMap.get(p.methodName).add(vMap);
            }
        }

    }

    private static void readFromFile(String filename) {
        patternList = new HashMap<String, HashMap<String, String>>();
        try {
            InputStream in = Evaluator.class.getClassLoader().getResourceAsStream(filename);
            BufferedReader br = new BufferedReader(new FileReader(new File(filename)));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("#", 0);
                HashMap<String, String> entry = patternList.get(parts[1]);
                if (entry == null) {
                    entry = new HashMap<String, String>();
                    patternList.put(parts[1], entry);
                }
                entry = patternList.get(parts[1]);
                entry.put(parts[0], parts[2]);
                patternList.put(parts[1], entry);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashSet<Pattern> getPatterns(String _method, String _class) {
        HashSet<Pattern> patterns = new HashSet<Pattern>();
        HashMap<String, String> result = new HashMap<String, String>();
        // construct the query
        String query;
        if (_class != null) {
            if (patternList.get(_method) == null)
                return null;
            result.put(_class, patternList.get(_method).get(_class));
        } else {
            result = patternList.get(_method);
        }

        for (String key : result.keySet()) {
            // populate HashMap with the results
            String className = key;
            String methodName = _method;
            String pattern = result.get(key);
            if (pattern == null)
                return null;
            Pattern p = new Pattern(1, className, methodName, pattern,
                    10000, true, "",
                    0, 0, "links");
            patterns.add(p);
        }

        return patterns;
    }

    private static HashMap<Pattern, ArrayList<Violation>> parseAndAnalyzeCodeSnippet(CodeSnippet cs) throws Exception {
        HashMap<String, HashSet<Pattern>> patterns = new HashMap<String, HashSet<Pattern>>();
        HashMap<Pattern, ArrayList<Violation>> vioMap = new HashMap<Pattern, ArrayList<Violation>>();

        // analyze the code snippets and return patterns to the plugin
        // parse and analyze a snippet using Maple
        String snippet = cs.getSnippet();
        if (!snippet.contains(";") && !(snippet.split(System.lineSeparator()).length > 1)) {
            throw new Exception("The code snippet is not valid.");
        }
        PartialProgramAnalyzer analyzer = new PartialProgramAnalyzer(snippet);
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

                String name = ((APICall) item).name;
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
                        ps = getPatterns(name.substring(4), type);
                    } else {
                        ps = getPatterns(name, type);
                    }
                    if (ps == null)
                        continue;
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
        // print patterns in vioMap
        for (Pattern p : vioMap.keySet()) {
            System.out.println("VIOLATION: " + p.pattern);
        }
        return vioMap;
    }
}
