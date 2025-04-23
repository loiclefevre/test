/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

public enum Action {
	GET_DATABASE_INFO(""),
	CREATE_DATABASE("Creating database..."),
	SKIP_TESTING("Analyzing committed files to skip tests eventually..."),
	DROP_DATABASE("Dropping database...");

	private final String banner;

	Action(final String banner) {
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}
}
