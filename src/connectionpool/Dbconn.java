package connectionpool;

import java.sql.*;
/*
 * Dbconn is for using connection pool more easily.
 */
public class Dbconn {
	public static String driver = "com.mysql.jdbc.Driver";
	public static String URL = "jdbc:mysql://127.0.0.1/pm?useunicode=true&characterEncoding=utf-8";
	public static String user = "root";
	public static String password = "";
	public ConnectionPool connPool = ConnectionPool.getInstance(driver, URL, user, password);
	
	Connection conn = null;
	public void debug() {
		this.connPool.debug();
	}

	public Dbconn() {
		try {
			connPool.createPool();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Connection getConnection() {
		try {
			if (conn == null)
				this.conn = connPool.getConnection();
			return conn;
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
		return null;
	}

	

	public void dispose() {
		if (conn != null) {
			connPool.returnConnection(this.conn);
			conn = null;
		}

	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		if (conn != null) {
			connPool.returnConnection(this.conn);
			conn = null;
		}

	}

	public static void main(String[] args) throws SQLException {
		
		
		for(int i=0;i<2;i++){
			Dbconn db = new Dbconn();
			Connection conn = db.getConnection();
			CallableStatement c = null;
			
			try {
				c = conn.prepareCall("{call log_in_by_username(?,?)}");
				c.setString(1, "q45hu");
				c.setString(2, "huqicheng");
				ResultSet rs = c.executeQuery();
				if(rs.next()){
					System.out.println("yeah");
				} else {
					System.out.println("nope");
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}finally{
				c.close();
				db.dispose();
			}

		}
		}
		
		

}
