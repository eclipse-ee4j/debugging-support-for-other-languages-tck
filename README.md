The TCK provides the source and the class file, which takes SMAP as an 
argument. The class can be used to test an unresolved SMAP file as well 
as a class file with an embedded SMAP.

The TCK does not provide the input files (.smap or .class) and need to 
be generated for testing

Build Instructions
====================

  Tools Required:
    - JDK 8+
    - Apache Ant 1.10.1+

  Steps for building TCK bundle:
    1. Set the environment variables:
       -------------------------------
       export JAVA_HOME=<JAVA_INSTALL_DIR>
       export ANT_HOME=<ANT_INSTALL_DIR>
       export PATH=$JAVA_HOME/bin:$ANT_HOME/bin:$PATH
       -------------------------------

    2. Run the ant command
        ---------------
        ant clean dist
        ---------------
   
    3. The generated bundle dsol-tck-<version>.zip can be found 
       under dist directory.
  
Running the TCK against Glassfish 5.0
========================================
  Tools Required:
    - JDK 8+
    - Apache Ant 1.10.1+
    - Glassfish 5.0+
  
  Steps for Testing GlassFish:

   1. Set the following init parameters for JSP page compiler and execution 
      servlet in domains/domain1/config/default-web.xml

      -----------------------------------------------------------------------
      <servlet>
        <servlet-name>jsp</servlet-name>
        <servlet-class>
           org.apache.jasper.servlet.JspServlet
        </servlet-class>
        ....
        <init-param>
          <param-name>keepgenerated</param-name>
          <param-value>true</param-value>
        </init-param>
        <init-param>
          <param-name>dumpSmap</param-name>
          <param-value>true</param-value>
        </init-param>
      -----------------------------------------------------------------------

    2. Start GlassFish server.

    3. Create a sample web module by packaging a sample JSP and deploy the 
       web module.

    4. Accessing the JSP to get the generated .smap file under the directory
       domains/domain1/generated/jsp/<web app name>/org/apache/jsp

    5. Run the smap verifier supplied with TCK
          -----------------------------------------------------------------------
          java VerifySMAP domains/domain1/generated/jsp/<web app name>/org/apache/jsp/Hello_jsp.class.smap
          -----------------------------------------------------------------------
          Assertion : The output of the java program should tell the .smap 
          file is correctly formatted one. Something like below 
          <GlassFish Home>/domains/domain1/generated/jsp/testclient/org/apache/jsp/Hello_jsp.class.smap
          is a correctly formatted SMAP

    Steps for Negative Testing :

    1. Edit the generated .smap file such that the syntax becomes invalid as per the specification, 
       and run VerifySMAP with that .smap file and look for the assertion failure. 

    2. Assertion #1 failed - SMAP syntax error
       -----------------------------------------------------------------------
       <GlassFish Home>/domains/domain1/generated/jsp/testclient/org/apache/jsp/Hello_jsp.class.smap:14: unexpected end of SMAP
        Exception in thread "main" VerifySMAP$AssertionViolationException: assertion #1 failed - SMAP syntax error
       -----------------------------------------------------------------------
