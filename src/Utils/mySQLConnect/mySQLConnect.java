package Utils.mySQLConnect;

import Config.Config;
import java.sql.DriverManager;
import java.sql.Connection;

public class mySQLConnect {
    public static Connection mySQLConnection() {
        try {
            String url = Config.mySQLUrl;
            String user = Config.mySQLuser;
            String password = Config.mySQLPassword;
            return DriverManager.getConnection(url, user, password);

        } catch (Exception e) {
            System.out.println(
                    "There is a error while establishing connection between java and mySQL : " + e.getMessage());
            return null;
        }
    }
}
