/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

public class GitHubCommittedFiles {

	private GitHubFilename[] files;
	public GitHubCommittedFiles() {
	}

	public GitHubFilename[] getFiles() {
		return files;
	}

	public void setFiles(GitHubFilename[] files) {
		this.files = files;
	}
}
