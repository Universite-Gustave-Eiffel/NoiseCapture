# syntax=docker/dockerfile:1.3-labs
FROM postgis/postgis:15-3.3

USER root

RUN <<EOF
sed -i 's|http:|https:|' /etc/apt/sources.list
sed -i 's|http:|https:|' /etc/apt/sources.list.d/*.list
apt-get update && apt-get install --yes postgresql-12-ogr-fdw
EOF

USER 1000:1000




