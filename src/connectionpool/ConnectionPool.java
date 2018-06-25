package connectionpool;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Vector;


public class ConnectionPool {
	private String jdbcDriver = ""; // driver of database
	private String dbUrl = ""; // url
	private String dbUsername = ""; // username
	private String dbPassword = ""; // password
	private String testTable = "";
	private int initialConnections = 1; // initial size of connection pool
	private int incrementalConnections = 0; // incremental of connections
	private int maxConnections = 50; // maximum size of the pool
	private Vector connections = null;

	private static ConnectionPool connectionPool = null;
	
	public void debug() {
		System.out.println("num:" + connections.size());
		Enumeration enumerate = connections.elements();
		while (enumerate.hasMoreElements()) {
			PooledConnection pConn = (PooledConnection) enumerate.nextElement();
			System.out.println(pConn.busy);
		}
	}

	private ConnectionPool(String jdbcDriver, String dbUrl, String dbUsername,
			String dbPassword) {
		this.jdbcDriver = jdbcDriver;
		this.dbUrl = dbUrl;
		this.dbUsername = dbUsername;
		this.dbPassword = dbPassword;
	}

	public static ConnectionPool getInstance(String jdbcDriver, String dbUrl, String dbUsername,
			String dbPassword){
		if (connectionPool == null) {
			connectionPool = new ConnectionPool(jdbcDriver, dbUrl, dbUsername, dbPassword);
		}
		
		return connectionPool;
	}
	
	public int getInitialConnections() {
		return this.initialConnections;
	}

	public void setInitialConnections(int initialConnections) {
		this.initialConnections = initialConnections;
	}

	public int getIncrementalConnections() {
		return this.incrementalConnections;
	}

	public void setIncrementalConnections(int incrementalConnections) {
		this.incrementalConnections = incrementalConnections;
	}

	public int getMaxConnections() {
		return this.maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public String getTestTable() {
		return this.testTable;
	}

	public void setTestTable(String testTable) {
		this.testTable = testTable;
	}

	public synchronized void createPool() throws Exception {
		// ensure that pool has not been created because it's a singleton
		if (connections != null) {
			return;
		}
		// instantiate the driver
		Driver driver = (Driver) (Class.forName(this.jdbcDriver).newInstance());
		DriverManager.registerDriver(driver); // register the jdbc driver
		// create the vector which manages all of PooledConnections
		connections = new Vector();
		// create the connection pool
		createConnections(this.initialConnections);
		System.out.println(" Create Database Connection Pool successfully！ ");
	}

	private void createConnections(int numConnections) throws SQLException {
		// create connections
		for (int x = 0; x < numConnections; x++) {
			// jump out of the loop if there are enough connections for the
			// initial pool
			if (this.maxConnections > 0
					&& this.connections.size() >= this.maxConnections) {
				break;
			}
			// add a new PooledConnection object to connections vector
			try {
				connections.addElement(new PooledConnection(newConnection()));
			} catch (SQLException e) {
				System.out.println(" Failed to create a new connection！ "
						+ e.getMessage());
				throw new SQLException();
			}
			System.out.println(" Connection has been created ......");
		}
	}

	private Connection newConnection() throws SQLException {
		// create a new connection for the pool
		Connection conn = DriverManager.getConnection(dbUrl, dbUsername,
				dbPassword);

		if (connections.size() == 0) {
			DatabaseMetaData metaData = conn.getMetaData();
			int driverMaxConnections = metaData.getMaxConnections();
			// set maxConnections as min(maxConnections, driverMaxConnections)
			if (driverMaxConnections > 0
					&& this.maxConnections > driverMaxConnections) {
				this.maxConnections = driverMaxConnections;
			}
		}
		return conn;
	}

	public synchronized Connection getConnection() throws SQLException {
		// ensure that pool has been created
		if (connections == null) {
			return null;
		}
		Connection conn = getFreeConnection(); // get a connection which is not
												// busy
		// if no connections is unbusy, wait for a moment
		while (conn == null) {
			wait(250);
			conn = getFreeConnection(); // it will create a new connection if no
										// free connection in the pool
		}
		return conn;
	}

	private Connection getFreeConnection() throws SQLException {
		// try to find a free connection
		Connection conn = findFreeConnection();
		if (conn == null) {
			// create some new connections
			createConnections(incrementalConnections);
			// find again
			conn = findFreeConnection();
			if (conn == null) {
				return null;
			}
		}
		return conn;
	}

	private Connection findFreeConnection() throws SQLException {
		Connection conn = null;
		PooledConnection pConn = null;
		// iterator
		Enumeration enumerate = connections.elements();
		while (enumerate.hasMoreElements()) {
			pConn = (PooledConnection) enumerate.nextElement();
			if (!pConn.isBusy()) {
				// get it and set it as busy
				conn = pConn.getConnection();
				pConn.setBusy(true);
				if (!testConnection(conn)) {
					try {
						conn = newConnection();
					} catch (SQLException e) {
						System.out
								.println(" Failed to create a new connection！ "
										+ e.getMessage());
						return null;
					}
					pConn.setConnection(conn);
				}
				break;
			}
		}
		return conn;
	}

	// to test if connection is usable
	private boolean testConnection(Connection conn) {
		try {

			if (testTable.equals("")) {
				conn.setAutoCommit(true);
			} else {
				// check if this connection is valid
				Statement stmt = conn.createStatement();
				stmt.execute("select count(*) from " + testTable);
			}
		} catch (SQLException e) {
			closeConnection(conn);
			return false;
		}

		return true;
	}

	public synchronized void returnConnection(Connection conn) {
		// ensure that pool has been created
		if (connections == null) {
			System.out.println(" Connection pool is not existed !");
			return;
		}
		// System.out.println("return connection");
		PooledConnection pConn = null;
		Enumeration enumerate = connections.elements();
		while (enumerate.hasMoreElements()) {
			pConn = (PooledConnection) enumerate.nextElement();
			if (conn == pConn.getConnection()) {
				pConn.setBusy(false);
				break;
			}
		}
	}

	public synchronized void refreshConnections() throws SQLException {
		// ensure that pool is created
		if (connections == null) {
			System.out.println(" Connection is not existed, cannot refresh !");
			return;
		}
		PooledConnection pConn = null;
		Enumeration enumerate = connections.elements();
		while (enumerate.hasMoreElements()) {
			pConn = (PooledConnection) enumerate.nextElement();
			if (pConn.isBusy()) {
				wait(5000);
			}
			closeConnection(pConn.getConnection());
			pConn.setConnection(newConnection());
			pConn.setBusy(false);
		}
	}

	public synchronized void closeConnectionPool() throws SQLException {
		// ensure that connection pool has been created
		if (connections == null) {
			System.out
					.println(" Connection pool is not existed, cannot close it !");
			return;
		}
		PooledConnection pConn = null;
		Enumeration enumerate = connections.elements();
		while (enumerate.hasMoreElements()) {
			pConn = (PooledConnection) enumerate.nextElement();
			if (pConn.isBusy()) {
				wait(5000);
			}

			closeConnection(pConn.getConnection());

			connections.removeElement(pConn);
		}

		connections = null;
	}

	private void closeConnection(Connection conn) {
		try {
			conn.close();
		} catch (SQLException e) {
			System.out.println(" Error when close a connection： "
					+ e.getMessage());
		}
	}

	@Override
	protected void finalize() throws Throwable {
		this.closeConnectionPool();
	}

	private void wait(int mSeconds) {
		try {
			Thread.sleep(mSeconds);
		} catch (InterruptedException e) {
		}
	}

}
