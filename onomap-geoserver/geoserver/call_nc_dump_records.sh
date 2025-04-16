#!/bin/bash

/usr/bin/curl --connect-timeout 36000 "http://localhost:8090/geoserver/ows?service=wps&version=1.0.0&request=Execute&Identifier=groovy:nc_dump_records&DataInputs=exportTracks%3Dtrue;exportMeasures%3Dtrue;exportAreas%3Dtrue;dayFilter%3D1" > dump.log 2>&1
