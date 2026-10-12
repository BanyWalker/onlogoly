package com.xiyuan.ontology.modeling.storage;

public class StorageRevisionConflict extends RuntimeException {

	private final int expected;

	private final int actual;

	public StorageRevisionConflict(int expected, int actual) {
		super("Layout revision conflict: expected " + expected + ", actual " + actual);
		this.expected = expected;
		this.actual = actual;
	}

	public int expected() {
		return this.expected;
	}

	public int actual() {
		return this.actual;
	}

}
