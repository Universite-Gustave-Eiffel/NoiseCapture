#!/usr/bin/env bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER onomap WITH PASSWORD '$POSTGRES_PASSWORD';
	CREATE DATABASE noisecapture;
	GRANT ALL PRIVILEGES ON DATABASE noisecapture TO onomap;
	\c noisecapture
	GRANT ALL ON SCHEMA public TO onomap;
	ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO onomap;
EOSQL

pgconf="$PGDATA/postgresql.conf"
hbaconf="$PGDATA/pg_hba.conf"

if ! grep -q "pgbackrest" "$pgconf"; then
  echo "wal_level = replica" >> $pgconf
  echo "archive_mode = on" >> $pgconf
  echo "archive_command = 'pgbackrest --stanza=noisecapture archive-push %p'" >> $pgconf
  echo "archive_timeout = 1d" >> $pgconf
  echo "max_wal_senders = 3" >> $pgconf
fi

pgbackrest --stanza=noisecapture --log-level-console=info stanza-create
