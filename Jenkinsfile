pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 45, unit: 'MINUTES')
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    parameters {
        booleanParam(name: 'RUN_SONAR', defaultValue: true, description: 'Analyze the repository and enforce the SonarQube quality gate.')
        booleanParam(name: 'BUILD_IMAGES', defaultValue: true, description: 'Build the three application container images after tests pass.')
        booleanParam(name: 'PUBLISH_IMAGES', defaultValue: false, description: 'Push images to GHCR (requires the ghcr-credentials Jenkins credential).')
    }

    environment {
        REGISTRY = 'ghcr.io'
        IMAGE_NAMESPACE = 'jassernoomani'
        IMAGE_VERSION = "${BUILD_NUMBER}"
        PLAYWRIGHT_BROWSERS_PATH = '/ms-playwright'
        CI = 'true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Quality checks') {
            parallel {
                stage('Spring Boot') {
                    steps {
                        dir('marketplace-api') {
                            sh 'mvn -B clean verify'
                        }
                    }
                }
                stage('FastAPI') {
                    steps {
                        dir('ai-service') {
                            sh '''
                                python3 -m venv .venv-ci
                                .venv-ci/bin/python -m pip install --upgrade pip
                                .venv-ci/bin/python -m pip install -r requirements.txt
                                .venv-ci/bin/python -m pytest --junitxml=test-results/pytest.xml --cov=app --cov-report=xml:coverage.xml
                            '''
                        }
                    }
                }
                stage('Angular') {
                    steps {
                        dir('marketplace-frontend') {
                            sh '''
                                npm ci
                                npm run test:coverage
                                npm run build
                                npx playwright test e2e/app.spec.ts
                            '''
                        }
                    }
                }
            }
        }

        stage('SonarQube analysis') {
            when { expression { params.RUN_SONAR } }
            steps {
                script {
                    def scannerHome = tool 'SonarScanner'
                    withSonarQubeEnv('E-biessa SonarQube') {
                        sh "${scannerHome}/bin/sonar-scanner"
                    }
                }
            }
        }

        stage('Quality gate') {
            when { expression { params.RUN_SONAR } }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build container images') {
            when { expression { params.BUILD_IMAGES } }
            steps {
                sh '''
                    docker build -t e-biessa-api:${IMAGE_VERSION} marketplace-api
                    docker build -t e-biessa-ai:${IMAGE_VERSION} ai-service
                    docker build -t e-biessa-web:${IMAGE_VERSION} marketplace-frontend
                '''
            }
        }

        stage('Publish container images') {
            when {
                allOf {
                    expression { params.BUILD_IMAGES }
                    expression { params.PUBLISH_IMAGES }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'ghcr-credentials', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
                    sh '''
                        echo "$GHCR_TOKEN" | docker login "$REGISTRY" -u "$GHCR_USER" --password-stdin
                        for service in api ai web; do
                          docker tag "e-biessa-${service}:${IMAGE_VERSION}" "${REGISTRY}/${IMAGE_NAMESPACE}/e-biessa-${service}:${IMAGE_VERSION}"
                          docker tag "e-biessa-${service}:${IMAGE_VERSION}" "${REGISTRY}/${IMAGE_NAMESPACE}/e-biessa-${service}:latest"
                          docker push "${REGISTRY}/${IMAGE_NAMESPACE}/e-biessa-${service}:${IMAGE_VERSION}"
                          docker push "${REGISTRY}/${IMAGE_NAMESPACE}/e-biessa-${service}:latest"
                        done
                        docker logout "$REGISTRY"
                    '''
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml,**/test-results/pytest.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'marketplace-api/target/*.jar,marketplace-frontend/dist/**/*,**/coverage.xml,**/jacoco.xml,marketplace-frontend/playwright-report/**/*'
        }
        success {
            echo 'All enabled quality gates passed. Images are local unless PUBLISH_IMAGES was explicitly selected.'
        }
    }
}
