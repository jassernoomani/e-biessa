#!/bin/sh
# Run from marketplace-api inside the project's Docker-based Jenkins agent.
# Dedicated disposable Redis: no application data, volumes, or published ports.
set -eu
if ! command -v docker >/dev/null 2>&1; then
    echo 'Docker CLI missing: rebuild Jenkins from devops/jenkins/Dockerfile before running CI.' >&2
    exit 1
fi
docker info >/dev/null
redis_id=''
cleanup() {
    if [ -n "$redis_id" ]; then docker rm -f "$redis_id" >/dev/null; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

redis_id=$(docker run -d --network "${JENKINS_TEST_NETWORK:-e-biessa-devops}" redis:7.4-alpine redis-server --save '' --appendonly no)
attempt=0
until docker exec "$redis_id" redis-cli ping | grep -q PONG; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        echo 'CI Redis did not become ready' >&2
        exit 1
    fi
    sleep 1
done
HA_REDIS_TEST_HOST=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$redis_id")
export HA_REDIS_TEST_HOST
export HA_REDIS_TEST_PORT=6379
mvn -B clean verify
# Give the standalone scanner the complete compile/runtime/test classpath.
mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies -DincludeScope=test -DoutputDirectory=target/sonar-libraries
