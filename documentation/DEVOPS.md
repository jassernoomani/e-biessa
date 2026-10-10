# E-biessa DevOps and observability

This is a local, academic CI/CD and monitoring environment. Jenkins is the
pipeline orchestrator, SonarQube enforces a quality gate, Prometheus collects
metrics, Grafana presents them, Alertmanager tracks alerts, and Blackbox
Exporter checks service availability. The pipeline creates deployable Docker
images; it does not deploy a public production system.

## 1. Prerequisites

- Docker Desktop running with Linux containers
- GitHub repository `https://github.com/jassernoomani/e-biessa`
- The E-biessa applications when live metrics are wanted: Angular on port
  4200, Spring on 8080, and FastAPI on 8001

Give Docker Desktop at least 6 GB RAM while Jenkins and SonarQube are running.
Ollama can remain stopped for ordinary CI because tests use isolated behavior.

## 2. Create local secrets

From the repository root in PowerShell:

```powershell
Copy-Item devops/.env.example devops/.env
notepad devops/.env
```

Replace both placeholder passwords. `devops/.env` is ignored by Git and is
separate from the Spring and FastAPI `.env` files.

## 3. Start the DevOps stack

```powershell
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml up -d --build
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml ps
```

The first Jenkins build downloads Node, Playwright Chromium, and plugins, so it
can take several minutes.

| Tool | URL | Purpose |
|---|---|---|
| Jenkins | http://localhost:8085 | CI/CD pipeline |
| SonarQube | http://localhost:9000 | static analysis and quality gate |
| Prometheus | http://localhost:9090 | metrics and PromQL |
| Grafana | http://localhost:3000 | provisioned E-biessa dashboard |
| Alertmanager | http://localhost:9093 | active and resolved alerts |

Retrieve Jenkins' one-time setup password:

```powershell
docker exec e-biessa-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Select **Install suggested plugins**, create the local administrator, and keep
the Jenkins URL as `http://localhost:8085/`.

SonarQube's initial local login is `admin` / `admin`; it immediately requires a
new password. The default is only for first login and is not in the repository.

## 4. Connect SonarQube to Jenkins

1. In SonarQube, open **My Account > Security**, create a token named
   `jenkins-e-biessa`, and copy it once.
2. In Jenkins, open **Manage Jenkins > Credentials > System > Global
   credentials**, add **Secret text**, paste the token, and use ID
   `sonarqube-token`.
3. Open **Manage Jenkins > System > SonarQube servers**, add a server named
   exactly `E-biessa SonarQube`, URL `http://sonarqube:9000`, and select the
   `sonarqube-token` credential.
4. Open **Manage Jenkins > Tools > SonarQube Scanner installations**, add an
   automatically installed scanner named exactly `SonarScanner`.
5. In SonarQube, open **Administration > Configuration > Webhooks** and add
   `http://jenkins:8080/sonarqube-webhook/`. The final slash is required.

## 5. Create the Jenkins pipeline

1. Select **New Item**, enter `e-biessa`, choose **Pipeline**, and save.
2. Under **Pipeline**, choose **Pipeline script from SCM** and **Git**.
3. Enter `https://github.com/jassernoomani/e-biessa.git` and branch `*/main`.
4. If private, add a GitHub personal-access-token credential and select it.
5. Keep **Script Path** as `Jenkinsfile`, save, then choose **Build Now**.

Jenkins polls the repository every five minutes, so a pushed commit starts CI
without exposing the local Jenkins server to the internet. **Build Now** remains
available when an immediate run is preferred.

The default run performs Maven/JaCoCo, Pytest coverage, Angular unit/build,
isolated Playwright, SonarQube analysis and quality gate, then three Docker
image builds.

`PUBLISH_IMAGES` is deliberately false. To demonstrate delivery through GHCR,
add a Jenkins username/password credential with ID `ghcr-credentials` (GitHub
username plus a token with `write:packages`), then use **Build with
Parameters**. Never put the token in the Jenkinsfile.

## 6. Verify monitoring

Run the three application services normally, then check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8080/actuator/prometheus
Invoke-RestMethod http://localhost:8001/health
Invoke-WebRequest http://localhost:8001/metrics/
```

In Prometheus, open **Status > Target health**. The Spring and FastAPI targets
should be green. Grafana automatically provisions the Prometheus data source
and **E-biessa / E-biessa Overview** dashboard. Blackbox checks the UI, APIs,
Jenkins, and SonarQube. A service down for two minutes creates a warning.

When applications are intentionally stopped, red targets and availability
alerts are expected; this demonstrates that monitoring is working.

## 7. Stop or reset the tools

Preserve all data while stopping the containers:

```powershell
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml stop
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml start
```

Remove containers and the network while retaining named volumes:

```powershell
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml down
```

Do not add `--volumes` unless you intentionally want to erase Jenkins,
SonarQube, Prometheus, and Grafana state.

## 8. Troubleshooting

### Backend coverage and Redis integration tests

The Spring stage runs `devops/jenkins/test-backend.sh` from `marketplace-api`.
It starts a disposable Redis 7.4 container on `e-biessa-devops`, waits for it
to be ready, and runs `mvn clean verify` with the Redis integration tests
enabled. No application database, persistent Redis volume, or host port is
used. A shell trap removes only that test container when the stage exits.
For a differently named agent network, set `JENKINS_TEST_NETWORK`.

The script also copies Maven dependencies to `target/sonar-libraries` for
the standalone SonarScanner. Java main/test binaries, dependencies, and
Surefire reports are configured in `sonar-project.properties`. Keep the
new-code coverage requirement at 80%; add tests rather than excluding code.

If Jenkins reports `Docker CLI missing`, ensure its Dockerfile explicitly
installs `docker-cli` and `docker-buildx`. Debian Trixie separates the client
from `docker.io`, and `--no-install-recommends` omits the client otherwise.
The Dockerfile checks both binaries during the build. When no build is
running, rebuild and recreate only Jenkins:

```powershell
docker compose --env-file devops/.env -f devops/docker-compose.devops.yml up -d --build --force-recreate jenkins
docker exec e-biessa-jenkins docker --version
docker exec e-biessa-jenkins docker info
```

This retains the named Jenkins home volume, including jobs and credentials.
Do not use `down --volumes`. The Docker socket grants powerful host access;
this setup is intended only for this trusted local academic environment.

Outside CI, Redis integration tests remain opt-in: point `HA_REDIS_TEST_HOST`
and `HA_REDIS_TEST_PORT` at a dedicated, disposable test Redis, then run
`mvn clean verify`. Never point these tests at application Redis. Without
`HA_REDIS_TEST_PORT`, the Redis integration tests are skipped; unit tests
still run.

- **SonarQube restarts:** increase Docker Desktop memory. On some WSL setups,
  run `wsl -d docker-desktop sysctl -w vm.max_map_count=524288` as an
  administrator, then restart the stack.
- **Application target is down:** start it and verify its URL from Windows.
  Prometheus reaches native services through `host.docker.internal`.
- **Quality gate waits forever:** verify the webhook and the shared
  `e-biessa-devops` network.
- **Playwright lacks Chromium:** rebuild Jenkins with `--no-cache`; the custom
  image installs the project's browser version.
- **Docker permission error:** this local Jenkins mounts the Docker socket and
  runs as root. That is acceptable for an isolated academic workstation, not a
  shared production host.

## Security and scope

Only health and metrics are anonymous; Spring's `info` actuator remains
authenticated. FastAPI uses route templates rather than raw URLs as metric
labels, preventing IDs, query strings, and tokens from becoming labels.
Credentials stay in ignored files or Jenkins' credential store. External
notifications and production deployment are intentionally outside this setup.
