# One-click local startup (Windows)

Double-click a file in the project root:

- **Start E-biessa.cmd**: Docker Desktop, existing XAMPP MySQL, Ollama when enabled, FastAPI, Spring Boot, Angular; opens localhost:4200.
- **Start DevOps.cmd**: Docker Desktop and the existing Jenkins/SonarQube/Prometheus/Grafana Compose stack; opens Grafana.
- **Start Everything.cmd**: both, in sequence. This is the most memory-intensive option.

Right-click a CMD file and select **Send to > Desktop (create shortcut)** for a desktop button. No administrator shell or virtualenv activation is normally needed. Execution-policy bypass applies only to the launched PowerShell process.

Existing healthy services are reused. Occupied unhealthy ports cause an error rather than killing processes. Background service output is in `tmp/launcher-logs` (ignored by Git). Closing the launcher window does not stop the servers. Do not delete database volumes or use `docker compose down -v` to stop services.

Angular's development server binds to IPv4 `0.0.0.0:4200` so Docker's monitoring probe can reach `http://host.docker.internal:4200`. The additional hostname is explicitly allowed in `marketplace-frontend/angular.json`; host checking is not disabled. Continue browsing at `http://localhost:4200`. This also makes the development server reachable on network interfaces permitted by Windows Firewall: use only a trusted local network, do not port-forward it, and do not treat it as a production server. Changes to these server options require restarting Angular; the launcher reuses an already-running healthy server.

This starts local source code, not GHCR images. It does not install dependencies, pull models/images, seed data, change secrets, or reset databases. Spring Boot retains its existing schema/initialization settings. MySQL is started with the existing `C:\xampp\mysql\bin\my.ini`; custom installations need a path adjustment in the script. The existing HA/demo stacks are not started. The local app does not require Redis unless its HA profile is enabled; that profile must use its separate workflow.

Prerequisites: existing `.env` files, installed frontend node_modules, FastAPI `.venv`, JDK (prefers the existing Microsoft JDK 21), Docker Desktop, XAMPP, and existing Ollama model. DevOps requires its images to have been built/pulled previously. Readiness does not preload ML models; the first AI request may take longer.

Check configuration without starting anything:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start-local.ps1 -Mode All -Check
```

For terminal use, add `-NoBrowser` to skip opening a browser. Application startup waits up to five minutes per service, reports the log location on failure, and leaves already-running processes untouched. If a timed-out service is still starting, check its logs before retrying.
