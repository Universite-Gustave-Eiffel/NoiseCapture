INSERT INTO noisecapture_area (pk_area, cell_q, cell_r, tzid, the_geom, laeq, la50, lden, mean_pleasantness,
                               measure_count, first_measure, last_measure, pk_party)
VALUES (12125502, -167652, 299791, 'Europe/London',
        'SRID=4326;POLYGON ((-4.144057871631671 51.69578338519233, -4.14417456621017 51.69582514583737, -4.144291260788668 51.69578338519233, -4.144291260788668 51.69569986378664, -4.14417456621017 51.695658103026, -4.144057871631671 51.69569986378664, -4.144057871631671 51.69578338519233))',
        63.43935001855943, 63.43935001855943, 0.0, 'NaN', 2, '2025-05-28 10:36:23+00', '2025-05-28 10:36:24+00', NULL);

INSERT INTO noisecapture_area_profile (pk_area,local_hour,laeq,uncertainty,variability,la50) VALUES
  (12125502,11,63.43935,255,0.0,63.43935);


