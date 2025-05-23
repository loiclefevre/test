/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

public enum Action {
	CREATE("Creating OCI resource..."),
	SKIP_TESTING("Analyzing committed files to skip tests eventually..."),
	DELETE("Deleting OCI resource...");

	private final String banner;

	Action(final String banner) {
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}
}
