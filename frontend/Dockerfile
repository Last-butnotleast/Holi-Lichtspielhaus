FROM node:24.21.0-bookworm-slim

RUN npm install --global pnpm@10.10.0 && \
    install -d -o node -g node /app

WORKDIR /app
USER node
COPY --chown=node:node package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/pnpm/store,uid=1000,gid=1000,sharing=locked \
    pnpm install --frozen-lockfile --store-dir /pnpm/store

COPY --chown=node:node . ./
EXPOSE 5173
CMD ["pnpm", "dev", "--host", "0.0.0.0", "--strictPort"]
