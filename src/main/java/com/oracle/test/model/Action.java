package com.oracle.test.model;

public enum Action {
	GET_DATABASE_INFO(""),
	CREATE_SCHEMA("Creating schema..."),
	SKIP_TESTING("Analyzing committed files to skip tests eventually..."),
	DROP_SCHEMA("Dropping schema...");

	private final String banner;

	Action(final String banner) {
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}
}
