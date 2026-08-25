# The backend image for the tailnet box (deploy/README.md).
#
# Built with `-Dquarkus.profile=demo`, and that is the load-bearing line: `crewcomp.dev-auth.enabled`
# is a BUILD-time property, so the authentication shim is compiled into this artefact and could not
# be added to a production one by a runtime override. An image built from this file is a demo image
# by construction — which is the property we want, stated where somebody will see it.
#
# Context is the repository root; see /.dockerignore.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Dependencies resolve in their own layer, so an ordinary rebuild — source changed, pom did not —
# re-runs only the compile. Deliberately a plain RUN rather than a BuildKit cache mount: this way
# the image builds under any builder the box happens to have, rather than failing to parse under
# the classic one.
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn ./.mvn
RUN ./mvnw -B -q de.qaware.maven:go-offline-maven-plugin:resolve-dependencies 2>/dev/null \
 || ./mvnw -B -q dependency:go-offline -DexcludeArtifactIds=quarkus-maven-plugin \
 || true

COPY backend/src ./src
# Tests are skipped deliberately: the ITs want a container runtime of their own, and this image is
# built from a revision CI has already run them against.
RUN ./mvnw -B -DskipTests -Dquarkus.profile=demo package

FROM eclipse-temurin:21-jre AS runtime
# curl is here for the container healthcheck alone, which is what lets `compose up --wait` mean
# "the API answers" rather than "a process started".
RUN apt-get update \
 && apt-get install --yes --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --create-home --home-dir /app crewcomp
WORKDIR /app

# The fast-jar layout, copied least-changing first so a rebuild reuses the dependency layer.
COPY --from=build --chown=1001:1001 /src/target/quarkus-app/lib/     ./lib/
COPY --from=build --chown=1001:1001 /src/target/quarkus-app/*.jar    ./
COPY --from=build --chown=1001:1001 /src/target/quarkus-app/app/     ./app/
COPY --from=build --chown=1001:1001 /src/target/quarkus-app/quarkus/ ./quarkus/

USER 1001
EXPOSE 8080

# No host port is published for this container (see docker-compose.yml): the only thing that can
# reach 8080 is the reverse proxy on the same compose network.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl --fail --silent http://127.0.0.1:8080/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
