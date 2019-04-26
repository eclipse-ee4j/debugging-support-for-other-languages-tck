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
    image: anajosep/cts-jaf:0.1
    command:
    - cat
    tty: true
    imagePullPolicy: Always
    resources:
      limits:
        memory: "8Gi"
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
