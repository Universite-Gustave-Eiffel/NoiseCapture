FROM alpine:3.13.2 AS builder

RUN <<EOF
    # Install all dependencies required for compiling busybox
    apk add git
EOF

RUN <<EOF
git clone --depth 1 https://github.com/Universite-Gustave-Eiffel/NoisePlanet.git
find /NoisePlanet -type f -name "*.html" -exec sed -i 's|https://onomap-gs\.noise-planet\.org|http://localhost:8080|g' {} +
EOF

FROM lipanski/docker-static-website:latest

COPY --from=builder /NoisePlanet/ .

CMD ["/busybox-httpd", "-f", "-v", "-p", "3000", "-c", "httpd.conf"]