env.label = "dsol-tck-ci-pod-${UUID.randomUUID().toString()}"
pipeline {
  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }
  agent {
    kubernetes {
      label "${env.label}"
      defaultContainer 'jnlp'
      yaml """
apiVersion: v1
kind: Pod
metadata:
spec:
  hostAliases:
  - ip: "127.0.0.1"
    hostnames:
    - "localhost.localdomain"
  containers:
  - name: dsol-tck-ci
    image: jakartaee/cts-dsol-base:0.1
    command:
    - cat
    tty: true
    imagePullPolicy: Always
    resources:
      limits:
        memory: "4Gi"
        cpu: "2.0"
"""
    }
  }
   parameters {
    string(name: 'GF_BUNDLE_URL', 
           defaultValue: '', 
           description: 'URL required for downloading GlassFish Full/Web profile bundle' )
    string(name: 'GF_VERSION_URL', 
           defaultValue: '', 
           description: 'URL required for downloading GlassFish version details' )
    string(name: 'TCK_BUNDLE_BASE_URL',
           defaultValue: '',
           description: 'Base URL required for downloading prebuilt binary TCK Bundle from a hosted location' )
    string(name: 'TCK_BUNDLE_FILE_NAME', 
           defaultValue: 'dsol-tck-1.0.0.zip', 
	   description: 'Name of bundle file to be appended to the base url' )
    choice(name: 'LICENSE', choices: 'EPL\nEFTL',
           description: 'License file to be used to build the TCK bundle(s) either EPL(default) or Eclipse Foundation TCK License' )
  }
  environment {
    ANT_OPTS = "-Djavax.xml.accessExternalStylesheet=all -Djavax.xml.accessExternalSchema=all -Djavax.xml.accessExternalDTD=file,http" 
  }
  stages {
    stage('dsol-tck-build') {
      steps {
        container('dsol-tck-ci') {
          sh """
            env
            bash -x ${WORKSPACE}/docker/build_dsoltck.sh
          """
          archiveArtifacts artifacts: 'bundles/*'
          stash includes: 'bundles/*', name: 'dsol-bundles'
        }
      }
    }

    stage('dsol-tck-run') {
      steps {
        container('dsol-tck-ci') {
          sh """
            env
            bash -x ${WORKSPACE}/docker/run_dsoltck.sh
          """
          archiveArtifacts artifacts: "dsoltck-junit-report.xml"
          junit testResults: '*junit-report.xml', allowEmptyResults: true
        }
      }
    }

  
  }
}
