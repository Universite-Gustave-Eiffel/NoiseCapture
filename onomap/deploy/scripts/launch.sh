#!/bin/bash

#
#  This file is part of the NoiseCapture application and OnoMap system.
#
#  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
#  citizen-contributed noise data.
#
#  This application is co-funded by the ENERGIC-OD Project (European Network for
#  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
#  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
#  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
#  Community. The application work is also supported by the French geographic portal GEOPAL of the
#  Pays de la Loire region (http://www.geopal.org).
#
#  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
#
#  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
#  GNU General Public License as published by the Free Software Foundation; either version 3 of
#  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
#  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
#  more details.You should have received a copy of the GNU General Public License along with this
#  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
#  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
#  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
#   or write to scientific.computing@univ-eiffel.fr
#

# This script is needed to change ownership and run the application as user onomap during the upgrade from version 24.2.0

# Change ownership of the WORKDIR to the onomap user and group
# Variables ONOMAP_<UID|GID> are defined in the Dockerfile and exported to the runtime environment
# PWD equals WORKDIR value from product Dockerfile

if [ "$(id -u)" -eq 0 ]; then
    TARGET_USER=${TARGET_USER:-onomap}
    TARGET_UID=${TARGET_UID:-$ONOMAP_UID}
    TARGET_GID=${TARGET_GID:-$ONOMAP_GID}

    chown -R $ONOMAP_UID:$ONOMAP_GID $PWD/workspace
    # Execute run-server.sh as the onomap user with the JAVA_HOME and PATH environment variables
    exec su "$TARGET_USER" -c "JAVA_HOME=$JAVA_HOME PATH=$PATH $APP_FILE"
else
    exec $APP_FILE
fi
