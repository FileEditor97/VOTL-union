package union.utils.database;

import ch.qos.logback.classic.Logger;

public class ConnectionUtil {

	private final String urlSQLite;

	protected final Logger logger;

	protected ConnectionUtil(String urlSQLite, Logger logger) {
		this.urlSQLite = urlSQLite;
		this.logger = logger;
	}

	protected String getUrlSQLite() {
		return urlSQLite;
	}
	
}
