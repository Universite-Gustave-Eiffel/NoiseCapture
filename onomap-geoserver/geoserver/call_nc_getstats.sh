#!/bin/bash

/usr/bin/curl -f --connect-timeout 36000 -H "Content-Type: text/xml" -o /home/onomap/NoisePlanet/data.noise-planet/statistics.json -X POST "http://localhost:8090/geoserver/ows?REQUEST=Execute&SERVICE=wps&VERSION=1.0.0&IDENTIFIER=groovy:nc_get_stats&RawDataOutput=result%3dformat%40mimetype%3dapplication%2Fjson"
