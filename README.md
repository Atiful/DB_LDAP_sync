# Search in Directories & DB Sync Project

## Project Overview
This project is designed to synchronize data between a MySQL Database and an LDAP Directory and also provide search functionality within the directory.

The system ensures that employee and contractor data stored in the database is properly reflected in the LDAP directory by performing add, update, and delete operations. It also allows users to search for records in LDAP using multiple search options.

This project helps maintain consistency between Database and LDAP and reduces manual administrative work.

---

## What This Project Does
- Authenticates user before allowing access to operations  
- Fetches employee data from MySQL and adds it into LDAP  
- Fetches contractor data from MySQL and adds it into LDAP  
- Syncs deleted users (if removed from DB, remove from LDAP as well)  
- Syncs updated users (if data changes in DB, update LDAP entry)  
- Maintains proper mapping using UID between DB and LDAP  
- Ensures no duplicate entries are created in LDAP  
- Handles CN changes during update operations  
- Provides multiple search features inside LDAP  

---

## Main Features
1. Secure password-based authentication  
2. DB → LDAP synchronization  
3. Employee & Contractor data handling  
4. Delete sync (removes inactive users from LDAP)  
5. Update sync (keeps LDAP data current)  
6. Group mapping support  
7. LDAP search module with multiple filters  

---

## How the System Works (High Level Flow)
1. System asks for authentication password  
2. After successful login, user can choose:
   - DB to LDAP operations
   - Search operations  
3. DB to LDAP operations include:
   - Add employees
   - Add contractors
   - Delete sync
   - Update sync  
4. Search module allows:
   - Search by user
   - Search by employee
   - Search by contractor
   - Group search
   - Advanced search  

---

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources  
    - inide this `src` 
      1. `Config` : this contains all the Configuration details about this project
      2. `Utils` : This act as a Helper function, inide this multiple other folder is present depending uopn the usecase for exmample 
         `LDAPUtils` : contains all helper function for LDAP related activities
         `mySQLConnect` : this folder contains all the mySQL connection related code
         `mySQLUtils` : This folder contains all SQL related code
      3. `LDAPFunctionTest.java` : This is the main file from where the program starts executing.

- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

---

## Dependency Management
The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found here:
https://github.com/microsoft/vscode-java-dependency#manage-dependencies

---

## Technologies Used
- Java  
- JDBC (MySQL Connector)  
- LDAP APIs  
- MySQL Database  
- LDAP Directory Server  (Apache)
- VS Code Java Project Structure  

---

## How to run it in local machine
follow the steps
## make sure you are inide then LDAPTest folder then run these command
1. cd .\src\
2. javac -cp ".;..\lib\mysql-connector-j-9.6.0.jar" LDAPFunctionTest.java
3. java -cp ".;..\lib\mysql-connector-j-9.6.0.jar" LDAPFunctionTest

---

## Use Case
This project is useful in organizations where:

- Employee data is stored in a database  
- LDAP is used for authentication and directory services  
- Data needs to be synchronized automatically  
- Admins need quick search access to directory records  

It helps keep both systems updated and consistent without manual effort.
