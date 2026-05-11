package Utils.mySQLUtils;

import java.sql.ResultSet;

public class mySQLUtils {

    // This function is used to print the SQL query ouput in readable format
    // @params rs this is the result what we get when we execute SQL select query
    // void , this just print the value

    public static void printSQLResultEmployee(ResultSet rs) {

        try {
            while (rs.next()) {
                System.out.println("uid : " + rs.getString("uid") + ", cn : " + rs.getString("cn") + ", sn : "
                        + rs.getString("sn") + ", mail : " + rs.getString("mail"));
            }
        } catch (Exception e) {
            System.out.println("There is a Error in printing SQL result Employee " + e.getMessage());
        }

    }
}