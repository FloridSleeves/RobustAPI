package checker.model;

import java.util.HashMap;

import com.google.common.collect.HashMultimap;

public class Class {
	public String repo;
	public String file;
	public String clazz;
	public HashMap<String, String> fields = new HashMap<String, String>();
	public HashMultimap<String, String> rev_fields = HashMultimap.create();

	public Class(String repo, String file, String name) {
		this.repo = repo;
		this.file = file;
		this.clazz = name;
	}
}
