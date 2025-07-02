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

RUN <<EOF
    #curl -L https://github.com/toymil/nginx_autoindex_xml_xslt_stylesheet/raw/refs/heads/trunk/autoindex.xslt --output /usr/share/nginx/niceindex.xslt
    sed -i '1i load_module modules/ngx_http_xslt_filter_module.so;' /etc/nginx/nginx.conf
    # Test configuration syntax
    nginx -t
EOF

COPY --from=builder /NoisePlanet/ /usr/share/nginx/html/
COPY --from=builder /ngx-niceindex/ /ngx-niceindex/

COPY default.conf /etc/nginx/conf.d/default.conf


