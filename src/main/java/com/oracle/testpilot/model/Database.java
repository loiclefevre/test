/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.model;

/**
 * @author LLEFEVRE
 * @since 1.0.0
 */
public class Database {
	private String database;
	private String host;
	private String service;
	private String password;
	private String version;

	public Database() {
	}

	public String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getService() {
		return service;
	}

	public void setService(String service) {
		this.service = service;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("Database{");
		sb.append("host='").append(host).append('\'');
		sb.append(", service='").append(service).append('\'');
		sb.append(", password='").append(password).append('\'');
		sb.append(", version='").append(version).append('\'');
		sb.append('}');
		return sb.toString();
	}
}
