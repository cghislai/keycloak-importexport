pipeline {
    agent any
    parameters {
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip tests')
        string(name: 'ALT_DEPLOYMENT_REPOSITORY', defaultValue: '', description: 'Alternative deployment repo')
        string(name: 'MVN_ARGS', defaultValue: '', description: 'Additional maven args')
        string(name: 'GPG_KEY_CREDENTIAL_ID', defaultValue: 'jenkins-jenkins-charlyghislain-maven-deploy-gpg-key',
                 description: 'Credential containing the private gpg key (pem)')
        string(name: 'GPG_KEY_FINGERPRINT', defaultValue: '508608F6CF097B4746CB291B5B72FDC1FF81F9ED',
         description: 'The fingerprint of this key to add to trust root')
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage ('Build') {
            steps {
                script {
                    env.MVN_ARGS=params.MVN_ARGS
                    if (params.ALT_DEPLOYMENT_REPOSITORY != '') {
                        env.MVN_ARGS="${env.MVN_ARGS} -DaltDeploymentRepository=${params.ALT_DEPLOYMENT_REPOSITORY}"
                    }
                }
                withMaven(maven: 'maven', mavenSettingsConfig: 'nexus-mvn-settings') {
                    sh "mvn -DskipTests=${params.SKIP_TESTS} clean compile install"
                }
            }
        }
        stage ('Publish') {
            steps {
                script {
                    env.MVN_ARGS=params.MVN_ARGS
                    env.MVN_ARGS="${env.MVN_ARGS} -DskipTests=true -Possrh-deploy"

                    if (params.ALT_DEPLOYMENT_REPOSITORY != '') {
                        env.MVN_ARGS="${env.MVN_ARGS} -DaltDeploymentRepository=${params.ALT_DEPLOYMENT_REPOSITORY}"
                    }
                    if (env.BRANCH_NAME == 'master') {
                        env.MVN_ARGS="${env.MVN_ARGS} "
                    }
                }
                withCredentials([file(credentialsId: "${params.GPG_KEY_CREDENTIAL_ID}", variable: 'GPGKEY')]) {
                    sh 'gpg --allow-secret-key-import --import $GPGKEY'
                    sh "echo \"${params.GPG_KEY_FINGERPRINT}:6:\" | gpg --import-ownertrust"
                }
                withMaven(maven: 'maven', mavenSettingsConfig: 'ossrh-settings-xml', jdk: 'jdk11') {
                    sh "mvn deploy $MVN_ARGS"
                }
            }
        }
    }
}
