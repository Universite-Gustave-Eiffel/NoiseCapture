FROM alpine:3.13.2 AS builder

RUN <<EOF
    # Install all dependencies required for compiling busybox
    apk add git
EOF

RUN <<EOF
git clone --depth 1 https://github.com/Universite-Gustave-Eiffel/NoisePlanet.git
find /NoisePlanet -type f -name "*.html" -exec sed -i 's|https://onomap-gs\.noise-planet\.org|http://localhost:8080|g' {} +
EOF

RUN <<EOF
  git clone https://github.com/reven/ngx-niceindex.git
EOF

COPY web/map_noisecapture/js/onomap.js /NoisePlanet/map_noisecapture/js/onomap.js

FROM docker.io/library/nginx:latest

COPY --from=builder /NoisePlanet/ /usr/share/nginx/html/
COPY --from=builder /ngx-niceindex/ /ngx-niceindex/

COPY nginx.conf /etc/nginx/conf.d/default.conf


