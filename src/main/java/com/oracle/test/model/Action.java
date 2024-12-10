package com.oracle.test.model;

public enum Action {
	CREATE_SCHEMA("Creating schema..."),
	SKIP_TESTING("Analyzing committed files to skip testing...");

	private final String banner;

	Action(final String banner) {
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}
}
