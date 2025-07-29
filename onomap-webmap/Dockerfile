FROM alpine:3.13.2 AS builder

RUN <<EOF
    # Install all dependencies required for compiling busybox
    apk add git
EOF

RUN <<EOF
git clone --depth 1 https://github.com/Universite-Gustave-Eiffel/NoisePlanet.git
EOF

COPY web/map_noisecapture/js/onomap.js /NoisePlanet/map_noisecapture/js/onomap.js

FROM docker.io/library/nginx:latest

RUN <<EOF
    sed -i '1i load_module modules/ngx_http_xslt_filter_module.so;' /etc/nginx/nginx.conf
    # Test configuration syntax
    nginx -t
EOF

COPY --from=builder /NoisePlanet/ /usr/share/nginx/html/
COPY ngx-niceindex /ngx-niceindex/

COPY default.conf /etc/nginx/conf.d/default.conf


