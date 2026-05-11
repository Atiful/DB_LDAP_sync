package Utils.LDAPUtils;

// represented a connected LDAP session (this is a interface)
// this class read the config files and  connect to LDAP and performs Bind (login)
import javax.naming.directory.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import javax.naming.Context; // contains constant
import javax.naming.NamingException; // LDAP error is being caught here
import javax.naming.NamingEnumeration;

import Config.Config;
import Utils.mySQLUtils.mySQLUtils;

public class LDAPUtil {

	// remove the user from the group also
	public static void removeUserFromAllGroups(DirContext context, String userDN, String groupBaseDN) {

		try {
			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

			String filter = "(objectClass=groupOfUniqueNames)";
			NamingEnumeration<SearchResult> results = context.search(groupBaseDN, filter, controls);

			while (results.hasMore()) {

				SearchResult sr = results.next();
				Attributes attrs = sr.getAttributes();

				Attribute members = attrs.get("uniqueMember");

				if (members != null) {

					for (int i = 0; i < members.size(); i++) {

						String memberDN = members.get(i).toString();

						if (memberDN.equalsIgnoreCase(userDN)) {

							ModificationItem[] mods = new ModificationItem[1];
							Attribute mod = new BasicAttribute("uniqueMember", userDN);

							mods[0] = new ModificationItem(
									DirContext.REMOVE_ATTRIBUTE,
									mod);
							context.modifyAttributes(sr.getNameInNamespace(), mods);
							System.out.println("Removed from group: " + sr.getNameInNamespace());
						}
					}
				}
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// make sure the user is removed from the group too
	public static void syncDeletedUsers(DirContext context, String baseDN, Set<String> dbIds) {
		try {
			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

			String filter = "(objectClass=inetOrgPerson)";
			NamingEnumeration<SearchResult> results = context.search(baseDN, filter, controls);

			String groupBaseDN = "ou=Groups,dc=example,dc=com";

			while (results.hasMore()) {
				SearchResult sr = results.next();
				Attributes attrs = sr.getAttributes();

				String empId = null;

				if (attrs.get("uid") != null) {
					empId = attrs.get("uid").get().toString(); // FIXED
				}
				// If LDAP user NOT in DB → delete
				if (empId != null && !dbIds.contains(empId)) {
					String userDN = sr.getNameInNamespace();
					removeUserFromAllGroups(context, userDN, groupBaseDN);
					context.destroySubcontext(userDN);
					System.out.println("Deleted from LDAP: " + empId);
				}

			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// get all Employye and Contractor details

	public static Set<String> getAllEmployeeIdsFromDB(ResultSet rs) {
		try {
			Set<String> dbIds = new HashSet<>();
			while (rs.next()) {
				dbIds.add(rs.getString("uid"));
			}
			return dbIds;
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return null;
		}
	}

	// sync for updated

	public static void syncUpdatedUsers(Statement st,
			DirContext context,
			String tableName,
			String baseDN) {
		try {

			ResultSet rs = st.executeQuery("SELECT * FROM " + tableName);

			while (rs.next()) {

				String uid = rs.getString("uid");
				String cn = rs.getString("cn");
				String sn = rs.getString("sn");
				String mail = rs.getString("mail");
				String groupAllowed = rs.getString("groupsAllowed").trim();

				// Search LDAP by uid
				String filter = "(uid=" + uid + ")";
				SearchControls sc = new SearchControls();
				sc.setSearchScope(SearchControls.SUBTREE_SCOPE);

				NamingEnumeration<SearchResult> results = context.search(baseDN, filter, sc);

				if (results.hasMore()) {

					SearchResult sr = results.next();
					String userDN = sr.getNameInNamespace();
					Attributes ldapAttrs = sr.getAttributes();

					List<ModificationItem> mods = new ArrayList<>();

					// CN check
					String ldapCN = ldapAttrs.get("cn").get().toString();

					if (!ldapCN.equals(cn)) {
						String newDN = "cn=" + cn + "," + baseDN;
						context.rename(userDN, newDN);
						userDN = newDN; // update reference
					}

					// SN check
					String ldapSN = ldapAttrs.get("sn").get().toString();
					if (!ldapSN.equals(sn)) {
						mods.add(new ModificationItem(
								DirContext.REPLACE_ATTRIBUTE,
								new BasicAttribute("sn", sn)));
					}

					// MAIL check
					String ldapMail = ldapAttrs.get("mail").get().toString();
					if (!ldapMail.equals(mail)) {
						mods.add(new ModificationItem(
								DirContext.REPLACE_ATTRIBUTE,
								new BasicAttribute("mail", mail)));
					}

					// Apply attribute updates
					if (!mods.isEmpty()) {
						context.modifyAttributes(userDN,
								mods.toArray(new ModificationItem[0]));

						System.out.println("Updated LDAP user: " + uid);
					}

					// ---- GROUP UPDATE ----
					removeUserFromAllGroups(context,
							userDN,
							"ou=Groups,dc=example,dc=com");
					addUserToGroup(context, groupAllowed, userDN);
				}
			}

		} catch (Exception e) {
			System.out.println("Update sync error: " + e.getMessage());
		}
	}

	/**
	 * This function will act as a staring point for create update and details
	 * details in LDAP Directory
	 * CRUD -> create , read , update , delete
	 * 
	 * @param st      SQL statement object
	 * @param context A LDAP connection
	 * @return void , just print statement based on user input
	 */

	public static void startCRUD(Statement st, DirContext context) {
		Scanner sc = new Scanner(System.in);

		System.out.println("please select the operation we want to do ");
		System.out.println("1. fetch Employee from Db and add in LDAP Directory");
		System.out.println("2. fetch Contractors from Db and add in LDAP Directory");
		System.out.println("3. Sync Deleted Users (Employee + Contractor)");
		System.out.println("4. Sync Updated Users (Employee + Contractor)");
		System.out.println("5. To exit");
		int number = sc.nextInt();

		while (number > 0 && number < 6) {

			switch (number) {
				case 1:
					try {
						ResultSet rs = st.executeQuery("Select * from Employee");
						mySQLUtils.printSQLResultEmployee(rs);
						addEmployee(st, context);
					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
					break;

				case 2:
					try {
						ResultSet rs = st.executeQuery("Select * from Contractors");
						mySQLUtils.printSQLResultEmployee(rs);
						addContractors(st, context);
					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
					break;

				case 3:
					try {
						// -------- EMPLOYEE SYNC --------
						ResultSet empRs = st.executeQuery("SELECT uid FROM Employee");
						Set<String> empIds = getAllEmployeeIdsFromDB(empRs);

						String empBaseDN = "ou=Employee,ou=Users,dc=example,dc=com";
						syncDeletedUsers(context, empBaseDN, empIds);

						// -------- CONTRACTOR SYNC --------
						ResultSet conRs = st.executeQuery("SELECT uid FROM Contractors");
						Set<String> conIds = getAllEmployeeIdsFromDB(conRs);

						String conBaseDN = "ou=Contractor,ou=Users,dc=example,dc=com";
						syncDeletedUsers(context, conBaseDN, conIds);

						System.out.println("Employee + Contractor sync completed");

					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
					break;

				case 4:
					try {
						// ----- Employee update sync -----
						syncUpdatedUsers(
								st,
								context,
								"Employee",
								"ou=Employee,ou=Users,dc=example,dc=com");

						// ----- Contractor update sync -----
						syncUpdatedUsers(
								st,
								context,
								"Contractors",
								"ou=Contractor,ou=Users,dc=example,dc=com");

						System.out.println("Update sync completed");

					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
					break;

				case 5:
					System.out.println("Exiting...");
					sc.close();
					return;

			}

			System.out.println("please select the operation we want to do ");
			System.out.println("1. fetch Employee from Db and add in LDAP Directory");
			System.out.println("2. fetch Contractors from Db and add in LDAP Directory");
			System.out.println("3. Sync Deleted Users (Employee + Contractor)");
			System.out.println("4. Sync Updated Users (Employee + Contractor)");
			System.out.println("5. To exit");
			number = sc.nextInt();
		}
		sc.close();
	}

	// add to group
	public static void addUserToGroup(DirContext context,
			String groupName,
			String userDN) {
		try {

			// DN of the group
			String groupDN = "cn=" + groupName + ",ou=Groups,dc=example,dc=com";

			ModificationItem[] mods = new ModificationItem[1];

			// Add USER DN into uniqueMember
			Attribute mod = new BasicAttribute("uniqueMember", userDN);

			mods[0] = new ModificationItem(
					DirContext.ADD_ATTRIBUTE,
					mod);

			context.modifyAttributes(groupDN, mods);

			System.out.println("Added " + userDN + " to group: " + groupName);

		} catch (Exception e) {
			System.out.println("Error adding user to group: " + e.getMessage());
		}
	}

	/**
	 * This function will add the Contractors in the LDAP Directory by fetching from
	 * DB
	 * 
	 * @param st      SQL statement object
	 * @param context A LDAP connection
	 * @return boolean if Contractors is successfully atlest one Contractor is added
	 *         then return true else
	 *         false.
	 */

	public static boolean addContractors(Statement st, DirContext context) {
		try {
			ResultSet rs = st.executeQuery("Select * from Contractors");

			while (rs.next()) {
				String uid = rs.getString("uid");
				String cn = rs.getString("cn");
				String sn = rs.getString("sn");
				String mail = rs.getString("mail");
				String groupsAllowed = rs.getString("groupsAllowed");

				// check if Contractors is present in Directory or not dublicate condition
				if (searchUser(context, cn, "Contractor", "directSearch")) {
					System.out.println("Contractors exist in Directory with same cn : " + cn);
					continue;
				}

				String userDN = "cn=" + cn + ",ou=Contractor,ou=Users,dc=example,dc=com";

				Attributes attrs = new BasicAttributes(true);// means case igonore
				Attribute objectClass = new BasicAttribute("objectClass");
				objectClass.add("top");
				objectClass.add("person");
				objectClass.add("organizationalPerson");
				objectClass.add("inetOrgPerson");

				attrs.put(objectClass);
				attrs.put("uid", uid);
				attrs.put("cn", cn);
				attrs.put("sn", sn);
				attrs.put("mail", mail);
				context.createSubcontext(userDN, attrs);
				addUserToGroup(context, groupsAllowed, userDN);
				System.out.println("Contractors added sucessfully : " + uid);
			}
			return true;
		} catch (Exception e) {
			System.out.println("There is a Error while inserting the values in Directory : " + e.getMessage());
			return false;
		}
	}

	/**
	 * This function will add the Employee in the LDAP Directory by fetching from DB
	 * 
	 * @param st      SQL statement object
	 * @param context A LDAP connection
	 * @return boolean if Employee is successfully at least one then return true
	 *         else
	 *         false.
	 */

	public static boolean addEmployee(Statement st, DirContext context) {
		try {
			ResultSet rs = st.executeQuery("Select * from Employee");

			while (rs.next()) {
				String uid = rs.getString("uid");
				String cn = rs.getString("cn");
				String sn = rs.getString("sn");
				String mail = rs.getString("mail");
				String groupsAllowed = rs.getString("groupsAllowed");

				// check if Employee is present in Directory or not dublicate condition
				if (searchUser(context, cn, "Employee", "directSearch")) {
					System.out.println("user exist in Directory with same cn : " + cn);
					continue;
				}

				String userDN = "cn=" + cn + ",ou=Employee,ou=Users,dc=example,dc=com";

				Attributes attrs = new BasicAttributes(true);// means case igonore
				Attribute objectClass = new BasicAttribute("objectClass");
				objectClass.add("top");
				objectClass.add("person");
				objectClass.add("organizationalPerson");
				objectClass.add("inetOrgPerson");

				attrs.put(objectClass);
				attrs.put("uid", uid);
				attrs.put("cn", cn);
				attrs.put("sn", sn);
				attrs.put("mail", mail);
				context.createSubcontext(userDN, attrs);
				addUserToGroup(context, groupsAllowed, userDN);
				System.out.println("user added sucessfully : " + uid);
			}
			return true;
		} catch (Exception e) {
			System.out.println("There is a Error while inserting the values in Directory : " + e.getMessage());
			return false;
		}
	}

	/**
	 * printing data in readable format by taking and printing the attribute values
	 * 
	 * @param results this is a take a single data as results.
	 * @return void , if value is present then just print it else dont.
	 */

	public static void derefenceAttribute(SearchResult results) {
		Attributes att = results.getAttributes();
		System.out.println();
		if (att.get("mail") != null)
			System.out.println(att.get("mail"));
		if (att.get("givenName") != null)
			System.out.println(att.get("givenName"));
		if (att.get("uid") != null)
			System.out.println(att.get("uid"));
		if (att.get("sn") != null)
			System.out.println(att.get("sn"));
		if (att.get("uniquemember") != null) {
			System.out.println(att.get("uniquemember"));
		}
		if (att.get("cn") != null) {
			System.out.println(att.get("cn"));
		}

	}

	/**
	 * This takes the whole data from the Directory and print it one by onw
	 * 
	 * @param results The whole data stream return by the Directory.
	 * @return void , if value is present then just print it else dont.
	 */

	public static void printUserDeatilsfromNumeration(NamingEnumeration<SearchResult> results) {

		try {
			int count = 0;
			while (results.hasMore()) {
				SearchResult data = results.next();
				count++;
				derefenceAttribute(data); // Details in simple language
				System.out.println("DN : " + data.getNameInNamespace()); // Data in raw format
			}
			System.out.println("------------Total output : " + count + " -----------------");
		} catch (NamingException e) {
			System.out.println("There is a error in printing the details from data Stream : " + e.getMessage());
		}

	}

	/**
	 * This is the function helps to search Users on the basic of filter
	 * 
	 * @param context This is a acitve LDAP connection used to perform the search
	 *                operation.
	 * @param keyword Search text used to filter users(e.g. name , uid , mail).
	 * @param ou      Orginazitional unit where the search will be performed (eg :
	 *                "Users" , "Contractors"),
	 * @param method  Define the type of search "withoutFilter" -> print all users
	 *                "withFilter" -> print using keyword
	 *                "advanceSearch" -> apply advanced conditions
	 *                "directSearch" -> it ensures that no wildcard is added to it .
	 *                use while search in AD from db
	 * @return boolean , if data is found then true else false and if true then
	 *         print the value too.
	 */

	public static boolean searchUser(DirContext context, String keyword, String ou, String method) {

		// validate the keyword make sure it does not contains any *
		keyword = keyword.replace("*", "");

		try {

			String baseDN;
			String filter;

			// all user
			if (ou.equals("Users") && method.equals("withoutFilter")) {
				baseDN = "ou=Users,dc=example,dc=com";
				filter = "(objectClass=inetOrgPerson)";
			}
			// then search in user by keyword
			else if (method.equals("withFilter")) {
				baseDN = "ou=Users,dc=example,dc=com";
				filter = "(&(objectClass=inetOrgPerson)(|(cn=*" + keyword + "*)(mail=*" + keyword + "*)(sn=*" + keyword
						+ "*)(uid=*" + keyword + "*)(givenName=*" + keyword + "*)))";
			} else if (ou.equals("Employee") && method.equals("withoutFilter")) {
				baseDN = "ou=Employee,ou=Users,dc=example,dc=com";
				filter = "(objectClass=inetOrgPerson)";

			} else if (ou.equals("Employee") && method.equals("withFilter")) {
				baseDN = "ou=Employee,ou=Users,dc=example,dc=com";
				filter = "(&(objectClass=inetOrgPerson)(|(cn=*" + keyword + "*)(mail=*" + keyword + "*)(sn=*" + keyword
						+ "*)(uid=*" + keyword + "*)(givenName=*" + keyword + "*)))";
			} else if (ou.equals("Contractor") && method.equals("withoutFilter")) {
				baseDN = "ou=Contractor,ou=Users,dc=example,dc=com";
				filter = "(objectClass=inetOrgPerson)";
			} else if (ou.equals("Contractor") && method.equals("directSearch")) {
				baseDN = "ou=Contractor,ou=Users,dc=example,dc=com";
				filter = "(&(objectClass=inetOrgPerson)(cn=" + keyword + "))";
			} else if (ou.equals("Employee") && method.equals("directSearch")) {
				baseDN = "ou=Employee,ou=Users,dc=example,dc=com";
				filter = "(&(objectClass=inetOrgPerson)(cn=" + keyword + "))";
			} else { // ou.equals("Contractor") && method.equals("withFilter")
				baseDN = "ou=Contractor,ou=Users,dc=example,dc=com";
				filter = "(&(objectClass=inetOrgPerson)(|(cn=*" + keyword + "*)(mail=*" + keyword + "*)(sn=*" + keyword
						+ "*)(uid=*" + keyword + "*)(givenName=*" + keyword + "*)))";
			}

			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

			NamingEnumeration<SearchResult> results = context.search(baseDN, filter, controls); // this search function
																								// return type is fixed

			// checking if data is present or not , if yes then print
			if (!results.hasMore()) {
				// System.out.println("no data is found in the Directatory");
				return false;
			} else
				printUserDeatilsfromNumeration(results);
		} catch (NamingException e) {
			System.out.println("search has failed see reason : " + e.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * Authenticates a user against an LDAP server.
	 *
	 * @param ldapUrl  The URL of the LDAP server .
	 * @param userDn   The Distinguished Name (DN) of the user .
	 * @param password The user's password.
	 * @return true if authentication is successful, false otherwise.
	 */

	public static DirContext authenticate(String ldapUrl, String userDn, String password) {

		Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, Config.INITIAL_CONTEXT_FACTORY); // java tells which libary to use
		env.put(Context.PROVIDER_URL, ldapUrl);
		env.put(Context.SECURITY_AUTHENTICATION, Config.SECURITY_AUTHENTICATION); // Use "simple" authentication
																					// with security feature
		env.put(Context.SECURITY_PRINCIPAL, userDn); // The user's DN , tell java how is login
		env.put(Context.SECURITY_CREDENTIALS, password); // The user's password

		try {
			// Attempt to create an InitialDirContext with the provided credentials
			return new InitialDirContext(env);
		} catch (NamingException e) {
			// An exception (e.g., AuthenticationException) indicates failure
			System.err.println("Authentication failed: " + e.getMessage());
			return null;
		}
	}

	/**
	 * This is the function helps to search Groups on the basic of filter.
	 *
	 * @param context This is a acitve LDAP connection used to perform the search
	 *                operation.
	 * @param keyword Search text used to filter users(e.g. name , uid , mail).
	 * @param method  Define the type of search
	 *                "withoutFilter" -> print all Groups
	 *                "withFilter" -> print using keyword
	 *                "advanceSearch" -> apply advanced conditions
	 * @param DN      if DN is passed then true else false
	 * @return void , if value is present then just print it else dont.
	 */

	public static void searchGroup(DirContext context, String keyword, String method, boolean DN) {

		try {

			String baseDN = "ou=Groups,dc=example,dc=com";
			String filter;
			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

			if (method.equals("withoutFilter")) {
				filter = "(objectClass=groupOfUniqueNames)";
			} else if (method.equals("withFilter") && !DN) {
				filter = "(&(objectClass=groupOfUniqueNames)(cn=*" + keyword + "*))";
			} else {
				filter = "(&(objectClass=groupOfUniqueNames)(uniqueMember=" + keyword + "))";
			}

			NamingEnumeration<SearchResult> results = context.search(baseDN, filter, controls);

			int count = 0;
			while (results.hasMore()) {
				count++;
				SearchResult data = results.next();
				derefenceAttribute(data);
			}
			if (count == 0) {
				System.out.println("no details found");
			} else
				System.out.println("------------Total output : " + count + " -----------------");
		} catch (NamingException e) {
			System.out.println("There is a error while printing Groups : " + e.getMessage());
		}
	}

	/**
	 * This is main entry point for this whole search feature of whole program . It
	 * guide the user based on user input
	 * 
	 *
	 * @param context This is a acitve LDAP connection used to perform the search
	 *                operation.
	 * @return void , it does not print any value , it just guide the user based on
	 *         user input.
	 */
	public static void searchCLP(DirContext context) {

		Scanner sc = new Scanner(System.in);
		System.out.println("What do want to Search ?");
		System.out.println("1. Search by User (" + Config.LDAPstructure + ")");
		System.out.println("2. Search by Employee");
		System.out.println("3. Search by Contractors");
		System.out.println("4. Search Groups");
		System.out.println("5. Advance Search");
		System.out.println("6. Exit");

		int input = sc.nextInt();
		String keyword = new String();
		String ou = new String(); // type of orginaztion // like Users/ Employee / Contractor
		String method = new String("withFilter"); // it takes 3 value withoutFilter and withFilter or AdvanceFilter
		while (input > 0 && input <= 5) {
			switch (input) {
				case 1:
					System.out.println("1. Show all User");
					System.out.println("2. Search by Keyword(age , name , commonName , mailId)");
					System.out.println("3. Exit");
					int value = sc.nextInt();

					ou = "Users";

					if (value == 1) {
						method = "withoutFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 2) {
						System.out.println("Enter keyword");
						keyword = sc.next();
						method = "withFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 3) {
						System.out.println("exiting....");
						sc.close();
						return;
					} else {
						System.out.println("invalid input");
						sc.close();
						return;
					}
					break;

				case 2:
					System.out.println("1. Show all Employee");
					System.out.println("2. Search by Keyword(age , name , commonName , mailId)");
					System.out.println("3. Exit");

					ou = "Employee";
					value = sc.nextInt();

					if (value == 1) {
						method = "withoutFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 2) {
						System.out.println("Enter keyword");
						keyword = sc.next();
						method = "withFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 3) {
						System.out.println("exiting....");
						sc.close();
						return;
					} else {
						System.out.println("invalid input");
						sc.close();
						return;
					}
					break;

				case 3:
					System.out.println("1. Show all Contractor");
					System.out.println("2. Search by Keyword(age , name , commonName , mailId)");
					System.out.println("3. Exit");

					ou = "Contractor";
					value = sc.nextInt();

					if (value == 1) {
						method = "withoutFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 2) {
						System.out.println("Enter keyword");
						keyword = sc.next();
						method = "withFilter";
						if (!searchUser(context, keyword, ou, method))
							System.out.println("no data found");
					} else if (value == 3) {
						System.out.println("exiting....");
						sc.close();
						return;
					} else {
						System.out.println("invalid input");
						sc.close();
						return;
					}
					break;

				case 4:
					System.out.println("1. Show all Groups");
					System.out.println(
							"2. Search by Keyword(i.e common name , uniqueMemeber).");
					System.out.println(
							"3. To check which user  has Access to which groups (please do give the DN).");
					System.out.println("4. Exit");
					// ou = "Employee";
					value = sc.nextInt();

					if (value == 1) {
						method = "withoutFilter";
						searchGroup(context, keyword, method, false);
					} else if (value == 2) {
						System.out.println("Enter keyword");
						keyword = sc.next();
						method = "withFilter";
						searchGroup(context, keyword, method, false);
					} else if (value == 3) {
						System.out.println("Enter Full DN");
						keyword = sc.next();
						method = "withFilter";
						searchGroup(context, keyword, method, true);
					} else if (value == 4) {
						System.out.println("exiting....");
						sc.close();
						return;
					} else {
						System.out.println("invalid input");
						sc.close();
						return;
					}

					break;

				case 5:
					HashMap<String, String> map = new HashMap<>();
					// order will be commonName , mailid , uid , Employeement type , want to check
					// if in a spefic group or not
					sc.nextLine();
					System.out.println("Press Enter to # to skip any fields");
					System.out.println("Enter common name");
					map.put("cn", sc.nextLine());
					System.out.println("Enter mailid");
					map.put("mail", sc.nextLine());
					System.out.println("Enter uid");
					map.put("uid", sc.nextLine());
					System.out.println("Enter type of Search (i.e Employee , Contractor, Groups Any)");
					map.put("ou", sc.nextLine());
					System.out.println("Enter the group common name");
					map.put("gcn", sc.nextLine());

					// if (map.get("ou").equals("Groups") || map.get("ou").equals("groups")) {
					// searchGroup(context, "", "AdvanceFilter", map);
					// }
					break;
				default:
					System.out.println("exit..");
					break;
			}

			System.out.println("What do want to Search ?");
			System.out.println("1. Search by User (" + Config.LDAPstructure + ")");
			System.out.println("2. Search by Employee");
			System.out.println("3. Search by Contractors");
			System.out.println("4. Search Groups");
			System.out.println("5. Advance Search");
			System.out.println("6. Exit");

			input = sc.nextInt();

		}
		sc.close();
	}
}