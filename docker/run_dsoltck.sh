#!/bin/bash -xe
#
# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0, which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# This Source Code may also be made available under the following Secondary
# Licenses when the conditions for such availability set forth in the
# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
# version 2 with the GNU Classpath Exception, which is available at
# https://www.gnu.org/software/classpath/license.html.
#
# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

which ant
ant -version

which java
java -version

unzip -o ${WORKSPACE}/bundles/dsol-tck-1.0.zip -d ${WORKSPACE}
unzip ${WORKSPACE}/dsol-tck-1.0.jar -d ${WORKSPACE}

cd $WORKSPACE

export GF_HOME="${WORKSPACE}"

WGET_PROPS="--progress=bar:force --no-cache"

#Install Glassfish
echo "Download and install GlassFish ..."
wget $WGET_PROPS $GF_BUNDLE_URL -O latest-glassfish.zip
rm -rf ${GF_HOME}/vi
mkdir -p ${GF_HOME}/vi
unzip -o ${WORKSPACE}/latest-glassfish.zip -d ${GF_HOME}/vi

export ADMIN_PASSWORD_FILE="${GF_HOME}/admin-password.txt"
echo "AS_ADMIN_PASSWORD=adminadmin" > ${ADMIN_PASSWORD_FILE}

echo "AS_ADMIN_PASSWORD=" > ${GF_HOME}/change-admin-password.txt
echo "AS_ADMIN_NEWPASSWORD=adminadmin" >> ${GF_HOME}/change-admin-password.txt

echo "" >> ${GF_HOME}/change-admin-password.txt

${GF_HOME}/vi/glassfish5/glassfish/bin/asadmin --user admin --passwordfile ${GF_HOME}/change-admin-password.txt change-admin-password

sed -i "s#<servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>#<servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>\n<init-param>\n<param-name>dumpSmap</param-name>\n<param-value>true</param-value>\n</init-param> #g" ${GF_HOME}/vi/glassfish5/glassfish/domains/domain1/config/default-web.xml

${GF_HOME}/vi/glassfish5/glassfish/bin/asadmin --user admin --passwordfile ${ADMIN_PASSWORD_FILE} start-domain

${GF_HOME}/vi/glassfish5/glassfish/bin/asadmin --user admin --passwordfile ${ADMIN_PASSWORD_FILE} deploy ${WORKSPACE}/bundles/testclient.war
curl http://localhost:8080/testclient/Hello.jsp

${GF_HOME}/vi/glassfish5/glassfish/bin/asadmin --user admin --passwordfile ${ADMIN_PASSWORD_FILE} stop-domain
${GF_HOME}/vi/glassfish5/glassfish/bin/asadmin --user admin --passwordfile ${ADMIN_PASSWORD_FILE} start-domain

$JAVA_HOME/bin/java VerifySMAP ${GF_HOME}/vi/glassfish5/glassfish/domains/domain1/generated/jsp/testclient/org/apache/jsp/Hello_jsp.class.smap > smap.log

output=$(grep "is a correctly formatted SMAP" smap.log | wc -l)
echo $output
if [[ "$output" < 1 ]];then
  failures="1"
  status="Failed"
else
  failures="0"
  status="Passed"
fi

echo "<testsuite id=\"1\" name=\"DSOL-TCK\" tests=\"1\" failures=\"${failures}\" errors=\"0\" disabled=\"0\" skipped=\"0\"" > $WORKSPACE/dsoltck-junit-report.xml
echo "<testcase name=\"VerifySMAP\" classname=\"VerifySMAP\" time=\"0\" status=\"${status}\"><system-out></system-out></testcase>" >> $WORKSPACE/dsoltck-junit-report.xml
echo "</testsuite>" >> $WORKSPACE/dsoltck-junit-report.xml
echo "" >> $WORKSPACE/dsoltck-junit-report.xml
chmod 777 $WORKSPACE/dsoltck-junit-report.xml

