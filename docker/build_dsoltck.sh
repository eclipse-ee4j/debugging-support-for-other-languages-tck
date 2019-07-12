#!/bin/bash -x
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


echo "ANT_HOME=$ANT_HOME"
echo "export JAVA_HOME=$JAVA_HOME"
echo "export PATH=$PATH"

export TS_HOME=$WORKSPACE

if [ ! -z "$TCK_BUNDLE_BASE_URL" ]; then
  #use pre-built tck bundle from this location to run test
  mkdir -p ${WORKSPACE}/bundles
  wget  --progress=bar:force --no-cache ${TCK_BUNDLE_BASE_URL}/${TCK_BUNDLE_FILE_NAME} -O ${WORKSPACE}/bundles/${TCK_BUNDLE_FILE_NAME}
  exit 0
fi


cd $WORKSPACE

which ant
ant -version

export ANT_OPTS="-DTS_HOME=$WORKSPACE -DJAVA_HOME=$JAVA_HOME -DJARPATH=$WORKSPACE"
export PATH="$ANT_HOME/bin:$JAVA_HOME/bin:$PATH"
if [[ "$LICENSE" == "EFTL" || "$LICENSE" == "eftl" ]]; then
	ant clean dist_eftl
else
	ant clean dist
fi
mkdir -p ${WORKSPACE}/bundles
chmod 777 ${WORKSPACE}/dist/*.zip
#cd $WORKSPACE/dist
cp ${WORKSPACE}/dist/* ${WORKSPACE}/bundles/
chmod -R 777 ${WORKSPACE}/bundles/
#for entry in `ls dsol*.zip`; do
#  date=`echo "$entry" | cut -d_ -f2`
#  strippedEntry=`echo "$entry" | cut -d_ -f1`
#  echo "copying ${WORKSPACE}/dist/$entry to ${WORKSPACE}/bundles/${strippedEntry}_latest.zip"
#  cp ${WORKSPACE}/dist/$entry ${WORKSPACE}/bundles/${strippedEntry}_latest.zip
#  chmod 777 ${WORKSPACE}/bundles/${strippedEntry}_latest.zip
#done
