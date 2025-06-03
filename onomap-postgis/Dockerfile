FROM imresamu/postgis:17-3.5

ENV PG_BACKREST_VERSION=2.35


RUN apt-get update \
      && apt-cache showpkg pgbackrest-$PG_BACKREST_VERSION \
      && apt-get install -y --no-install-recommends \
           pgbackrest-$PG_BACKREST_VERSION \
      && rm -rf /var/lib/apt/lists/*
