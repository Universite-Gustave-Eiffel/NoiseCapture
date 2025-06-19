#!/usr/bin/env bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER onomap WITH PASSWORD '$POSTGRES_PASSWORD';
	ALTER DATABASE noisecapture OWNER TO onomap;

	CREATE USER metabase WITH PASSWORD '$METABASE_PASSWORD';
	CREATE DATABASE metabase;
	ALTER DATABASE metabase OWNER TO metabase;
	GRANT CONNECT ON DATABASE noisecapture TO metabase;
  /c noisecapture
  GRANT USAGE ON SCHEMA public TO metabase;
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase;
  GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO metabase;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO metabase;
EOSQL

pgconf="$PGDATA/postgresql.conf"
hbaconf="$PGDATA/pg_hba.conf"

if ! grep -q "pgbackrest" "$pgconf"; then
  echo "wal_level = replica" >> "${pgconf}"
  echo "archive_mode = on" >> "${pgconf}"
  echo "archive_command = 'pgbackrest --stanza=noisecapture archive-push %p'" >> "${pgconf}"
  echo "max_wal_senders = 3" >> "${pgconf}"
  echo "max_wal_size = 16GB" >> "${pgconf}"
fi

pgbackrest --stanza=noisecapture --log-level-console=info stanza-create
