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
 /**
  * Modified from BetterWms :
  * https://gist.github.com/rclark/6908938
  * Hexagon formulae from :
  * http://www.redblobgames.com/grids/hexagons/
  */
L.TileLayer.OnoMap = L.TileLayer.WMS.extend({
   //http://www.redblobgames.com/grids/hexagons

	hexOverlay : null,
	// Hex size
	size : 15.,
	// While moving the mouse do not redraw if hex coordinate is the same
	lastDrawnHex : {q:0, r:0},


	/**
	 * @param hex Hex index
	 * @return Local coordinate of hexagon index
	 */
	hex_to_meter : function (hex) {
		x = this.size * Math.sqrt(3.) * (hex.q + hex.r/2.);
		y = this.size * 3./2. * hex.r;
		return {x:x, y:y};
	},
		
	/**
	 * @param center Hex center position
	 * @param size Hex size
	 * @param i Vertex [0-5]
	 * @return Vertex coordinate
	 */
	hex_corner : function (center, size, i) {
		var angle_deg = 60. * i   + 30.;
		var angle_rad = Math.PI / 180. * angle_deg;
		return {x:center.x + size * Math.cos(angle_rad), y:center.y + size * Math.sin(angle_rad)};
	 },

	/**
	 * @param hex Hex index
	 * @return x,y,z cube index
	 */
	hex_to_cube : function (h) {
		var x = h.q
		var z = h.r
		var y = -x-z
		return {x:x, y:y, z:z}
	},

	/**
	 * @param h Cube index
	 * @return q,r hex index
	 */
	cube_to_hex : function (h) {
		var q = h.x
		var r = h.z
		return {q:q, r:r}
	},
		
	/**
	 * @param h aproximate cube index
	 * @return h Cube index
	 */
	cube_round : function (h) {
		var rx = Math.round(h.x);
		var ry = Math.round(h.y);
		var rz = Math.round(h.z);

		var x_diff = Math.abs(rx - h.x);
		var y_diff = Math.abs(ry - h.y);
		var z_diff = Math.abs(rz - h.z);

		if (x_diff > y_diff && x_diff > z_diff)
			rx = -ry-rz
		else if (y_diff > z_diff)
			ry = -rx-rz
		else
			rz = -rx-ry

		return {x:rx, y:ry, z:rz}
	},

	/**
	 * @param h aprox hex index
	 * @return h hex index
	 */
	hex_round : function (h) {
		return this.cube_to_hex(this.cube_round(this.hex_to_cube(h)));
	},

	/**
	 * @param x x coordinate
	 * @param y y coordinate
	 * @return q,r hex index
	 */
	meterToHex : function(x, y) {
		var q = (x * Math.sqrt(3.)/3. - y / 3.) / this.size;
		var r = y * 2./3. / this.size;
		return this.hex_round({q:q, r:r});
	},

	/**
	 *	Mousemove callback function updating labels and input elements
	 */
	updateHexOverlay : function (evt) {
		var pos = evt.latlng,
			opts = this.options;
		if (pos) {
			pos = pos.wrap();
			var gPos = proj4('EPSG:3857', [pos.lng, pos.lat]);
			var hPos = this.meterToHex(gPos[0], gPos[1]);
			if(this.lastDrawnHex.q != hPos.q || this.lastDrawnHex.r != hPos.r) {
				//console.log("EPSG:3857 = " + gPos + "; EPSG:4326 = " + pos)
				if(this.hexOverlay) {
					map.removeLayer(this.hexOverlay)
				}
				this.lastDrawnHex = hPos;
				// Compute corners of hex
				var center = this.hex_to_meter(hPos)
				var vertices = []			
				for(i=0;i<6;i++) {
					var v = this.hex_corner(center, this.size, i);
					
					var vGPos = proj4('EPSG:3857','EPSG:4326', [v.x, v.y]);
					
					if(isNaN(vGPos[0]) || isNaN(vGPos[1])) {
						return;
					}
					vertices.push(new L.LatLng(vGPos[1],vGPos[0]));
				}
				// Close the ring
				vertices.push(vertices[0]);
				this.hexOverlay = new L.Polyline(vertices, {
				color: 'gray',
				clickable: false,
				weight: 3,
				opacity: 0.5,
				smoothFactor: 1
				});
				this.hexOverlay.addTo(map);
			}
		}
	},

  onAdd: function (map) {
    // Triggered when the layer is added to a map.
    //   Register a click listener, then do all the upstream WMS things
    L.TileLayer.WMS.prototype.onAdd.call(this, map);
    map.on('click', this.getFeatureInfo, this);
	map.on("mousemove", this.updateHexOverlay, this);
  },
  
  onRemove: function (map) {
    // Triggered when the layer is removed from a map.
    //   Unregister a click listener, then do all the upstream WMS things
    L.TileLayer.WMS.prototype.onRemove.call(this, map);
    map.off('click', this.getFeatureInfo, this);
	map.off("mousemove", this.updateHexOverlay, this);
  },
  
  getFeatureInfo: function (evt) {
    // Make an AJAX request to the server and hope for the best
    var url = this.getFeatureInfoUrl(evt.latlng),
        showResults = L.Util.bind(this.showGetFeatureInfo, this);
    $.ajax({
      url: url,
      success: function (data, status, xhr) {
        var err = typeof data === 'string' ? null : data;
        showResults(err, evt.latlng, data);
      },
      error: function (xhr, status, error) {
        showResults(error);  
      }
    });
  },
  
  getFeatureInfoUrl: function (latlng) {
    // Construct a GetFeatureInfo request URL given a point
    var point = this._map.latLngToContainerPoint(latlng, this._map.getZoom()),
        size = this._map.getSize(),
        
        params = {
          request: 'GetFeatureInfo',
          service: 'WMS',
          srs: 'EPSG:4326',
          styles: this.wmsParams.styles,
          transparent: this.wmsParams.transparent,
          version: this.wmsParams.version,      
          format: this.wmsParams.format,
          bbox: this._map.getBounds().toBBoxString(),
          height: size.y,
          width: size.x,
          layers: this.wmsParams.layers,
          query_layers: this.wmsParams.layers,
          //info_format: 'application/vnd.ogc.gml/3.1.1'
		  info_format: 'text/html'
        };
    
    params[params.version === '1.3.0' ? 'i' : 'x'] = point.x;
    params[params.version === '1.3.0' ? 'j' : 'y'] = point.y;
    
    return this._url + L.Util.getParamString(params, this._url, true);
  },
  
  showGetFeatureInfo: function (err, latlng, content) {
    if (err) { console.log(err); return; } // do nothing if there's an error
    // Otherwise show the content in a popup, or something.
    L.popup({ maxWidth: 800})
      .setLatLng(latlng)
      .setContent(content)
      .openOn(this._map);
  }
});

L.tileLayer.OnoMap = function (url, options) {
  return new L.TileLayer.OnoMap(url, options);  
};