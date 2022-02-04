pipeline {
    agent any
    parameters {
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip tests')
        booleanParam(name: 'NPM_DEPLOY', defaultValue: false, description: 'NPM deployment')
        string(name: 'ALT_DEPLOYMENT_REPOSITORY', defaultValue: '', description: 'Alternative deployment repo')
        string(name: 'MVN_ARGS', defaultValue: '', description: 'Additional maven args')
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
                    env.NPM_DEPLOY=params.NPM_DEPLOY
                    env.MVN_ARGS="${env.MVN_ARGS} -DskipTests=true"
                    if (params.ALT_DEPLOYMENT_REPOSITORY != '') {
                        env.MVN_ARGS="${env.MVN_ARGS} -DaltDeploymentRepository=${params.ALT_DEPLOYMENT_REPOSITORY}"
                    }
                    if (env.BRANCH_NAME == 'master') {
                        env.MVN_ARGS="${env.MVN_ARGS} -Possrh-deploy"
                        env.NPM_DEPLOY="true"
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
