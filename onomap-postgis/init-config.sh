#!/usr/bin/env bash
pgconf="$PGDATA/postgresql.conf"
hbaconf="$PGDATA/pg_hba.conf"

if ! grep -q "pgbackrest" "$pgconf"; then
  echo "wal_level = replica" >> $pgconf
  echo "archive_mode = on" >> $pgconf
  echo "archive_command = 'pgbackrest --stanza=app archive-push %p'" >> $pgconf
  echo "archive_timeout = 1d" >> $pgconf
  echo "max_wal_senders = 3" >> $pgconf
fi
export PGUSER=onomap
pgbackrest --stanza=noisecapture --log-level-console=info stanza-create
