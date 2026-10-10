FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl gnupg git maven python3 python3-pip python3-venv docker.io \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
       | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_22.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN npx --yes playwright@1.56.1 install --with-deps chromium \
    && chmod -R a+rX /ms-playwright

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Debian Trixie splits the client from docker.io; --no-install-recommends
# does not install it implicitly. Keep this late to reuse the tooling cache.
RUN apt-get update && apt-get install -y --no-install-recommends docker-cli docker-buildx \
    && docker --version && docker buildx version \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
