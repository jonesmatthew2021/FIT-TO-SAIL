# The admin SPA, built to static files and served by Caddy, which also reverse-proxies /api to
# the backend so the browser sees one origin — the arrangement vite.config.ts reasons about, and
# the one ADR 0003's SameSite session cookie will need.
#
# Context is the repository root; see /.dockerignore.

FROM node:22-slim AS build
WORKDIR /src
COPY admin-web/package.json admin-web/package-lock.json ./
RUN npm ci
COPY admin-web/ ./
# `npm run build` is `tsc --noEmit && vite build`, so a type error fails the image build rather
# than shipping a bundle nothing checked.
#
# A production bundle, deliberately: `import.meta.env.DEV` is false, so the dev sign-in and the
# "Switch role" button are compiled out. Which role the box presents is therefore the backend's
# CREWCOMP_DEV_AUTH_DEFAULT_ROLES — see deploy/README.md, "Showing one role".
RUN npm run build

FROM caddy:2-alpine AS runtime
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv/www
