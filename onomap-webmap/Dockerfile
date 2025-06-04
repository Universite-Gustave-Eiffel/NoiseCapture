FROM nginx AS builder

RUN <<EOF
    find /etc/apt -type f -name "*.list" -exec sed -i.bak 's|http:|https:|g' {} +
    find /etc/apt -type f -name "*.sources" -exec sed -i.bak '/^\s*URIs:/ s|http:|https:|g' {} +
    apt-get update
    apt-get install -y --no-install-recommends git
    apt-get clean
    rm -rf /var/lib/apt/lists/*
EOF

RUN git clone --depth 1 https://github.com/Universite-Gustave-Eiffel/NoisePlanet.git

FROM nginx

WORKDIR /usr/share/nginx/html

COPY --from=builder /NoisePlanet/map_noisecapture/ ./
