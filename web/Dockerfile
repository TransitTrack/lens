# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@11 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm run generate

FROM nginx:alpine AS runtime

COPY --from=build /workspace/.output/public /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Reverse-proxy target for the relative `/graphql` calls the Apollo client makes
# (see app/plugins/apollo.client.ts) — the built static bundle has no way to
# know the backend's address itself, so nginx fills that gap. Overridable per
# environment without a rebuild; the nginx image's own entrypoint envsubst's
# *.template files in /etc/nginx/templates/ using only real env vars (nginx's
# own $uri/$host/etc. are left untouched since they aren't env vars).
ENV BACKEND_URL=http://localhost:8080

EXPOSE 80
