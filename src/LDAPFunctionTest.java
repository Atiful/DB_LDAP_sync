import java.util.*;
import java.sql.*;

import Utils.LDAPUtils.*;
import Utils.mySQLConnect.mySQLConnect;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import Config.Config;

public class LDAPFunctionTest {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("please enter your password to validate");
        String password = sc.next();

        System.out.println("please select the value depending upon the usecase");
        System.out.println("1. To do any operation from database to Directory");
        System.out.println("2. To search any details in Directory");
        int number = sc.nextInt();

        DirContext context = LDAPUtil.authenticate(Config.url, Config.user, password);

        switch (number) {
            case 1:
                Connection on = mySQLConnect.mySQLConnection();
                if (on != null && context != null) {
                    System.out.println("mySQL is connected sucessfully and user is authenicate sucessfully");
                    try {
                        Statement st = on.createStatement(); // create a path to execute a SQL quary
                        LDAPUtil.startCRUD(st, context);
                    } catch (Exception e) {
                        System.out.println("There is a problem while extablishing connection : " + e.getMessage());
                    } finally {
                        try {
                            on.close();
                        } catch (Exception e) {
                            System.out.println("There is a problem while extablishing connection : " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("mySQL is not connected or and user not authenicate");
                }
                break;

            case 2:
                if (context != null) {
                    System.out.println("User authenticated successfully!");
                    LDAPUtil.searchCLP(context);// all the CLP will perfrom here (main function
                    try {
                        context.close();
                    } catch (NamingException e) {
                        System.out.println("something went wrong in closing the conncetion : " + e.getMessage());
                    }
                } else {
                    System.out.println("User authentication failed.");
                }
                break;

            default:
                System.out.println("invalid input");
        }

        sc.close();
    }
}
