FROM imresamu/postgis:17-3.5

# use https instead of http then install pgbackrest

RUN <<EOF
find /etc/apt -type f -name "*.list" -exec sed -i.bak 's|http:|https:|g' {} +
find /etc/apt -type f -name "*.sources" -exec sed -i.bak '/^\s*URIs:/ s|http:|https:|g' {} +
apt-get update
apt-cache showpkg pgbackrest
apt-get install -y --no-install-recommends pgbackrest
apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

COPY ./init-config.sh /docker-entrypoint-initdb.d/20_pgbackrest.sh
COPY ./pgbackrest.conf /etc/pgbackrest/pgbackrest.conf
