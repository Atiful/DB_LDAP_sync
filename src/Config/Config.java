package Config;

public class Config {
    public static final String url = "ldap://localhost:10389";
    public static final String user = "uid=admin,ou=system";
    public static final String pwd = "Atiful";
    public static final String INITIAL_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    public static final String SECURITY_AUTHENTICATION = "simple";
    public static final String LDAPstructure = "Employee/Contractor";
    public static final String mySQLUrl = "jdbc:mysql://localhost:3306/company";
    public static final String mySQLPassword = "Atiful26h27";
    public static final String mySQLuser = "root";
}