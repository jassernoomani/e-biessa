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
