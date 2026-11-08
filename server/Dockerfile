# Build from the repository root so the catalog is included:
#   docker build -f server/Dockerfile -t cosmetic-studio-sync .
FROM node:24-alpine AS build
WORKDIR /app/server
COPY server/package.json server/package-lock.json ./
RUN npm ci
COPY server/tsconfig.json ./
COPY server/src ./src
COPY server/test ./test
RUN npx tsc -p .

FROM node:24-alpine
WORKDIR /app/server
ENV NODE_ENV=production \
    CATALOG_DIR=/app/catalog \
    DB_PATH=/data/cosmetics.db \
    PORT=8080
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev && mkdir -p /data && chown node:node /data
COPY --from=build /app/server/dist ./dist
COPY catalog /app/catalog
USER node
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://127.0.0.1:8080/v1/health || exit 1
CMD ["node", "--disable-warning=ExperimentalWarning", "dist/src/index.js"]
