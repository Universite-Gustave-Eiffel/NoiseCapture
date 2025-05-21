----- PostGIS only query

CREATE INDEX ON NOISECAPTURE_POINT USING GIST(THE_GEOM);
CREATE INDEX ON NOISECAPTURE_AREA USING GIST(THE_GEOM);
CREATE INDEX ON NOISECAPTURE_AREA_CLUSTER USING GIST(THE_GEOM);

 ---- Force SRID

SELECT UpdateGeometrySRID('noisecapture_dump_track_envelope','the_geom',4326);
SELECT UpdateGeometrySRID('noisecapture_area','the_geom',4326);
SELECT UpdateGeometrySRID('noisecapture_point','the_geom',4326);
SELECT UpdateGeometrySRID('noisecapture_area_cluster','the_geom',4326);
