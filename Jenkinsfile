    pipeline {
        agent {
            docker {
                image 'gradle:4.10.3-jdk8-alpine'
                args '-v gradle-cache:/home/gradle/.gradle'
            }
        }

        stages {
            stage('clean') {
                steps {
                    dir('build/libs') {
                        deleteDir()
                    }
                }
            }

            stage('build') {
                steps {
                    sh 'gradle build --no-daemon'
                }
            }
        }

        post {
            success {
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            }
        }
    }