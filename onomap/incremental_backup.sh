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

# Define the tar file name with current year and week number
TAR_FILE="/onomap/workspace/onomap_archive_tar/full_raw_$(date +%Y_%U).tar"

# Snapshot file names
DIFF_SNAR="/onomap/workspace/backup_diff.snar"
INC_SNAR="/onomap/workspace/backup_inc.snar"

# Handle snar files based on tar file existence
if [ -f "$TAR_FILE" ]; then
    # TAR_FILE exists - mid-week update
    if [ -f "$INC_SNAR" ]; then
        echo "Updating weekly backup: Using $INC_SNAR as base"
        cp -f "$INC_SNAR" "$DIFF_SNAR"
    fi
else
    # TAR_FILE doesn't exist - new week starting
    if [ -f "$DIFF_SNAR" ]; then
        echo "Starting new weekly backup: Committing previous state"
        cp -f "$DIFF_SNAR" "$INC_SNAR"
    else
        echo "Starting first weekly backup"
    fi
fi

# Create/update the tar archive
tar --create --listed-incremental="$DIFF_SNAR" --file="$TAR_FILE" --totals /onomap/workspace/onomap_archive/

if [ ! -f "$INC_SNAR" ]; then
  # It is the first TAR_FILE ever created as there is no incremental snar
  cp -f "$DIFF_SNAR" "$INC_SNAR"
  mv "$TAR_FILE" /onomap/workspace/onomap_archive_tar/full_raw.tar
fi
