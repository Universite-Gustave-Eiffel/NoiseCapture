/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */
GeoJSONCluster = L.GeoJSON.extend({
    ready: false,
    onAdd: function (map) {
        
        var _this = this;
        
        // Triggered when the layer is added to a map.
        L.GeoJSON.prototype.onAdd.call(this, map);
        
        this.options.onEachFeature = function(feature, layer) {
            var count = feature.properties.measure_count;
            var abbrev = count >= 10000 ? Math.round(count / 1000) + 'k' :
            count >= 1000 ? (Math.round(count / 100) / 10) + 'k' : count;
            var size =
              count < 100 ? 'small' : count < 1000 ? 'medium' : 'large';
            var icon = L.divIcon({
            html: '<div><span>' + abbrev + '</span></div>',
            className: 'hexagon-cluster hexagon-cluster-' + size,
            iconSize: L.point(40, 40)
            });

            var label = L.marker(layer.getBounds().getCenter(), { icon: icon, keyboard: false , interactive: false});
            L.GeoJSON.prototype.addLayer.call(_this, label);
        };
        
        this.ready = true;
        
        map.on('moveend', this.update, this);
        
        this.update();
    },
    
    loadGeoJson: function (data) {
        if(this.ready) {
            L.GeoJSON.prototype.clearLayers.call(this);
            L.GeoJSON.prototype.addData.call(this, data);
        }
    },
    
    update: function (data) {        
       if(this._map && this.ready && this._map.getZoom() < 15) {
          var _this = this;
          var geoJsonUrl = this.options.wfs_url;
          var defaultParameters = {
              service: 'WFS',
              version: '1.0.0',
              request: 'getFeature',
              typeName: 'noisecapture:noisecapture_area_cluster',
              maxFeatures: 3000,
              outputFormat: 'application/json'
              };

          var zoom_lvl_to_cell_lvl = [10, 10, 10, 10, 9, 8, 8, 7, 7, 6, 6, 5, 4, 3, 3];
          var bbox = map.getBounds();
          var customParams = {
              CQL_FILTER: 'BBOX(the_geom, '+bbox.toBBoxString()+') AND "cell_level"=' + zoom_lvl_to_cell_lvl[map.getZoom()]
              };
          var parameters = L.Util.extend(defaultParameters, customParams);

          $.ajax({
              url: geoJsonUrl + L.Util.getParamString(parameters),
              datatype: 'json',
              jsonCallback: 'getJson',
              success: function (data, status, xhr) {
                          _this.loadGeoJson(data);
                       }
              });
        } else {
          L.GeoJSON.prototype.clearLayers.call(this);
        }
    },
});

L.geoJSON.OnoMap = function (url) {
    return new GeoJSONCluster(null, {  wfs_url : url,     
    style: function (geoJsonFeature) {
          var count = geoJsonFeature.properties.measure_count;
          return { stroke : true,
                  interactive: false,
                  weight: 0.4,
                  color: "#ffffff",
                  bubblingMouseEvents: false,
                  fillOpacity: 0.6,
                  fillColor: count < 100 ? "rgb(210,223,255)" :
                  count < 1000 ? "rgb(176,201,255)" : "rgb(73,155,255)"}
      }});
};
