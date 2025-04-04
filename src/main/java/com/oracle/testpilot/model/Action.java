/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

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
