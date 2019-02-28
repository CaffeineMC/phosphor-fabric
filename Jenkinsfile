pipeline {
    agent {
        docker {
            image 'gradle:4.10.3-jdk8-alpine'
            args '-v gradle-cache:/home/gradle/.gradle'
        }
    }

    stages {
        stage('Clean') {
            steps {
                dir('build/libs') {
                    deleteDir()
                }
            }
        }

        stage('Build') {
            steps {
                sh 'gradle build'
            }
        }

        stage('Publish') {
            when {
                branch 'master'
            }

            environment {
                MAVEN_SECRETS_FILE = credentials('maven-secrets')
            }

            steps {
                sh 'gradle publish'
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
        }
    }
}