FROM caddy:2.10.2-builder AS builder
# The plugin needs Go 1.25; let Go fetch the toolchain if the image's one is older.
ENV GOTOOLCHAIN=auto
RUN xcaddy build v2.10.2 \
    --with github.com/caddy-dns/vercel@90ab6e6ed7835757bc5caf78168456c461d54b74

FROM caddy:2.10.2
COPY --from=builder /usr/bin/caddy /usr/bin/caddy