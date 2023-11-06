package checker.model;

import java.util.ArrayList;
import java.util.HashMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;

public class Method {
	public String repo;
	public String file;
	public String clazz;
	public String method;

	// var -> type
	public HashMap<String, String> locals = new HashMap<String, String>();
	// type -> vars
	public HashMultimap<String, String> rev_locals = HashMultimap.create();

	public ArrayList<String> seq = new ArrayList<String>();

	// api -> arguments (var or another api)
	public HashMap<String, HashMultiset<MethodCall>> args = new HashMap<String, HashMultiset<MethodCall>>();
	// argument (var or another api) -> apis
	public HashMap<String, HashMultiset<MethodCall>> rev_args = new HashMap<String, HashMultiset<MethodCall>>();

	// var (lhs) -> vars or apis or both (rhs)
	public HashMap<String, HashMultiset<Assignment>> assigns = new HashMap<String, HashMultiset<Assignment>>();
	// var or api (rhs) -> vars (lhs)
	public HashMap<String, HashMultiset<Assignment>> rev_assigns = new HashMap<String, HashMultiset<Assignment>>();

	// api -> vars or apis
	public HashMap<String, HashMultiset<Receiver>> receivers = new HashMap<String, HashMultiset<Receiver>>();
	// var or api -> apis
	public HashMap<String, HashMultiset<Receiver>> rev_receivers = new HashMap<String, HashMultiset<Receiver>>();

	public HashMap<String, HashMultiset<Predicate>> predicates = new HashMap<String, HashMultiset<Predicate>>();

	public Method(String repo, String file, String className, String methodName) {
		this.repo = repo;
		this.file = file;
		this.clazz = className;
		this.method = methodName;
	}
}
