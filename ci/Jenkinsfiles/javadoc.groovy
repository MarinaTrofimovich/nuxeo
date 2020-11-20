/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 *     Thomas Roger <troger@nuxeo.com>
 *     Kevin Leturc <kleturc@nuxeo.com>
 */

repositoryUrl = 'https://github.com/nuxeo/nuxeo'

properties([
  [$class: 'GithubProjectProperty', projectUrlStr: repositoryUrl],
  [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5']],
  disableConcurrentBuilds(),
])

void setGitHubBuildStatus(String context, String message, String state) {
  if (env.DRY_RUN != "true") {
    step([
      $class: 'GitHubCommitStatusSetter',
      reposSource: [$class: 'ManuallyEnteredRepositorySource', url: repositoryUrl],
      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: context],
      statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: message, state: state]]],
    ])
  }
}

String getMavenArgs() {
  def args = '-B -nsu -Dnuxeo.skip.enforcer=true'
  if (!isPullRequest()) {
    args += ' -Prelease'
  }
  return args
}

def isPullRequest() {
  return BRANCH_NAME =~ /PR-.*/
}

String getVersion() {
  return isPullRequest() ? getPullRequestVersion() : getReleaseVersion()
}

String getReleaseVersion() {
  String nuxeoVersion = readMavenPom().getVersion()
  String noSnapshot = nuxeoVersion.replace('-SNAPSHOT', '')
  String version = noSnapshot + '.0' // first version ever

  // find the latest tag if any
  sh "git fetch origin 'refs/tags/v${noSnapshot}*:refs/tags/v${noSnapshot}*'"
  def tag = sh(returnStdout: true, script: "git tag --sort=taggerdate --list 'v${noSnapshot}*' | tail -1 | tr -d '\n'")
  if (tag) {
    container('maven') {
      version = sh(returnStdout: true, script: "semver bump patch ${tag} | tr -d '\n'")
    }
  }
  return version
}

String getPullRequestVersion() {
  return "${BRANCH_NAME}-" + readMavenPom().getVersion()
}

String getDockerTagFrom(String version) {
  return version.tokenize('.')[0] + '.x'
}

void dockerPull(String image) {
  sh "docker pull ${image}"
}

void dockerRun(String image, String command, String user = null) {
  String userOption = user ? "--user=${user}" : ''
  sh "docker run --rm ${userOption} ${image} ${command}"
}

void dockerTag(String image, String tag) {
  sh "docker tag ${image} ${tag}"
}

void dockerPush(String image) {
  sh "docker push ${image}"
}

def rolloutStatus(kind, name, timeout, namespace) {
  sh """
    kubectl rollout status ${kind} ${name} \
      --timeout=${timeout} \
      --namespace=${namespace}
  """
}

def rolloutStatusRedis(namespace) {
  rolloutStatus('statefulset', "${TEST_REDIS_K8S_OBJECT}", "${TEST_DEFAULT_ROLLOUT_STATUS_TIMEOUT}", namespace)
}

pipeline {
  agent {
    label 'jenkins-nuxeo-package-11'
  }
  options {
    timeout(time: 1, unit: 'HOURS')
  }
  environment {
    // force ${HOME}=/root - for an unexplained reason, ${HOME} is resolved as /home/jenkins though sh 'env' shows HOME=/root
    HOME = '/root'
    HELM_CHART_REPOSITORY_NAME = 'local-jenkins-x'
    HELM_CHART_REPOSITORY_URL = 'http://jenkins-x-chartmuseum:8080'
    HELM_CHART_NUXEO = 'nuxeo'
    NUXEO_JAVADOC_IMAGE_NAME = 'nuxeo-javadoc'
    MAVEN_OPTS = "$MAVEN_OPTS -Xms2g -Xmx3g -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    MAVEN_ARGS = getMavenArgs()
    VERSION = getVersion()
    DOCKER_TAG = getDockerTagFrom("${VERSION}")
    CHANGE_BRANCH = "${env.CHANGE_BRANCH != null ? env.CHANGE_BRANCH : BRANCH_NAME}"
    CHANGE_TARGET = "${env.CHANGE_TARGET != null ? env.CHANGE_TARGET : BRANCH_NAME}"
    // jx step helm install's --name and --namespace options require alphabetic chars to be lowercase
    PREVIEW_NAMESPACE = "nuxeo-preview-${BRANCH_NAME.toLowerCase()}"
  }

  stages {
    stage('Set labels') {
      when {
        branch 'PR-*'
      }
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Set Kubernetes resource labels
          ----------------------------------------
          """
          echo "Set label 'branch: ${BRANCH_NAME}' on pod ${NODE_NAME}"
          sh """
            kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME}
          """
          // set branch name in Helm chart values used for the unit tests
          sh """
            for valuesFile in ci/helm/*.yaml; do
              envsubst < \$valuesFile > \$valuesFile~gen
            done
          """
          // output pod description
          echo "Describe pod ${NODE_NAME}"
          sh """
            kubectl describe pod ${NODE_NAME}
          """
        }
      }
    }

    stage('Update version') {
      when {
        branch 'PR-*'
      }
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Update version
          ----------------------------------------
          New version: ${VERSION}
          """
          sh """
            # root POM
            mvn ${MAVEN_ARGS} -Pdistrib,docker versions:set -DnewVersion=${VERSION} -DgenerateBackupPoms=false
            perl -i -pe 's|<nuxeo.platform.version>.*?</nuxeo.platform.version>|<nuxeo.platform.version>${VERSION}</nuxeo.platform.version>|' pom.xml
            perl -i -pe 's|org.nuxeo.ecm.product.version=.*|org.nuxeo.ecm.product.version=${VERSION}|' server/nuxeo-nxr-server/src/main/resources/templates/nuxeo.defaults
          """
        }
      }
    }

    stage('Build Javadoc') {
      when {
        branch 'PR-*'
      }
      steps {
        setGitHubBuildStatus('javadoc/build', 'Build Javadoc', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Build Javadoc
          ----------------------------------------"""
          echo "MAVEN_OPTS=$MAVEN_OPTS"
          sh "mvn ${MAVEN_ARGS} -V -T4C -Pjavadoc -DskipTests install"
          sh "mvn ${MAVEN_ARGS} -f server/pom.xml -Pjavadoc -DskipTests install"
        }
      }
      post {
        success {
          setGitHubBuildStatus('javadoc/build', 'Build Javadoc', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('javadoc/build', 'Build Javadoc', 'FAILURE')
        }
      }
    }

    stage('Generate Nuxeo ECM Javadoc') {
      when {
        branch 'PR-*'
      }
      steps {
        setGitHubBuildStatus('javadoc/site', 'Generate Javadoc site', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Generate Nuxeo ECM Javadoc
          ----------------------------------------"""
          sh "mvn ${MAVEN_ARGS} -Pjavadoc site"
        }
      }
      post {
        success {
          setGitHubBuildStatus('javadoc/site', 'Generate Javadoc site', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('javadoc/site', 'Generate Javadoc site', 'FAILURE')
        }
      }
    }

    stage('Deploy Nuxeo ECM Javadoc') {
      when {
        branch 'PR-*'
        expression {
          return pullRequest.labels.contains('preview-javadoc')
        }
        not {
          environment name: 'DRY_RUN', value: 'true'
        }
      }
      steps {
        setGitHubBuildStatus('javadoc/preview', 'Deploy Javadoc environment', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Build Nuxeo ECM Javadoc Docker Image ${VERSION}
          ----------------------------------------
          Image tag: ${VERSION}"""
          sh "mv target/site/apidocs ci/docker/javadoc/apidocs"
          dir('ci/docker/javadoc') {
            sh "skaffold build -f skaffold.yaml"
          }

          echo """
          ----------------------------------------
          Deploy Nuxeo ECM Javadoc Environment
          ----------------------------------------
          Image tag: ${VERSION}
          Namespace: ${PREVIEW_NAMESPACE}"""
          dir('ci/helm/javadoc') {
            script {
              // first substitute environment variables in chart values
              sh """
                mv values.yaml values.yaml.tosubst
                envsubst < values.yaml.tosubst > values.yaml
              """
              // second create target namespace (if doesn't exist) and copy secrets to target namespace
              boolean nsExists = sh(returnStatus: true, script: "kubectl get namespace ${PREVIEW_NAMESPACE}") == 0
              if (!nsExists) {
                sh "kubectl create namespace ${PREVIEW_NAMESPACE}"
              }
              sh "kubectl --namespace platform get secret kubernetes-docker-cfg -ojsonpath='{.data.\\.dockerconfigjson}' | base64 --decode > /tmp/config.json"
              sh """kubectl create secret generic kubernetes-docker-cfg \
                  --namespace=${PREVIEW_NAMESPACE} \
                  --from-file=.dockerconfigjson=/tmp/config.json \
                  --type=kubernetes.io/dockerconfigjson --dry-run -o yaml | kubectl apply -f -"""
              // third build and deploy the chart
              sh """
                jx step helm build --verbose
                jx step helm install --namespace ${PREVIEW_NAMESPACE} --name javadoc --verbose .
              """
              url = sh(returnStdout: true, script: "kubectl get svc --namespace ${PREVIEW_NAMESPACE} javadoc -o go-template='{{index .metadata.annotations \"fabric8.io/exposeUrl\"}}'")
              echo """
              ----------------------------------------
              Javadoc Environment available at: ${url}
              ----------------------------------------"""
              // comment the PR if it is the first time
              if (isPullRequest() && !nsExists) {
                pullRequest.comment("Preview Javadoc environment available [here](${url}).");
              }
            }
          }
        }
      }
      post {
        success {
          setGitHubBuildStatus('javadoc/preview', 'Deploy Javadoc environment', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('javadoc/preview', 'Deploy Javadoc environment', 'FAILURE')
        }
      }
    }

  }
}
