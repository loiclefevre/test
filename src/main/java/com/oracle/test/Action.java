package com.oracle.test;

public enum Action {
	CREATE_SCHEMA("Creating schema..."),
	SKIP_TESTING("Analyzing commit...");

	private final String banner;

	Action(final String banner) {
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}
}
