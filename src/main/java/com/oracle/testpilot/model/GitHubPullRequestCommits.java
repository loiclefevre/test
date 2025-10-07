/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

/**
 * @author LLEFEVRE
 * @since 1.0.21
 */
public class GitHubPullRequestCommits {
	private String url;

	public GitHubPullRequestCommits() {
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
