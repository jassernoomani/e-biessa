```dockerfile
FROM jenkins/jenkins:lts-jdk21

USER root

# Install required CI/CD tools:
# - Docker CLI
# - Git
# - Maven
# - Python
# - Node.js 22
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        git \
        maven \
        python3 \
        python3-pip \
        python3-venv \
        docker.io \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
        | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_22.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Playwright browser location
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Install the exact Playwright version used by the project
RUN npx --yes playwright@1.56.1 install --with-deps chromium \
    && chmod -R a+rX /ms-playwright

# Install Jenkins plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

RUN jenkins-plugin-cli \
        --plugin-file /usr/share/jenkins/ref/plugins.txt

# Keep the image running as root.
# docker-compose.devops.yml also explicitly uses:
#   user: root
#
# This allows Jenkins to access the Docker socket mounted by Compose.
```