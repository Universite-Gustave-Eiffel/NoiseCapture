'use strict';

function debounce(func, wait, immediate) {
	var timeout;
	return function() {
		var context = this, args = arguments;
		var later = function() {
			timeout = null;
			if (!immediate) func.apply(context, args);
		};
		var callNow = immediate && !timeout;
		clearTimeout(timeout);
		timeout = setTimeout(later, wait);
		if (callNow) func.apply(context, args);
	};
};

var map = L.map('map').fitWorld();

L.control.scale({
    'position': 'bottomright',
    'metric': true,
    'imperial': false
}).addTo(map);

function featureToMarker(feature, latlng) {
        if (!feature.properties.cluster)  return L.circleMarker(latlng, {
            fillColor: feature.properties["marker-color"],
            stroke: false,
            fillOpacity: 1.,
            radius: 5,
            zIndex: 4
        });
    var count = feature.properties.point_count;
    var size =
        count < 100 ? 'small' :
        count < 1000 ? 'medium' : 'large';
    var icon = L.divIcon({
        html: '<div><span>' + feature.properties.point_count_abbreviated + '</span></div>',
        className: 'marker-cluster marker-cluster-' + size,
        iconSize: L.point(40, 40)
    });
    return L.marker(latlng, {icon: icon});
}

function featureToMarkerBackground(feature, latlng) {
        if (!feature.properties.cluster)  return L.circleMarker(latlng, {
            stroke: false,
            fillColor: '#ffffff',
            fillOpacity: 1.,
            radius: 6,
            zIndex: 4
        });
    return L.featureGroup([]);
}
var userMeasurementPointsStroke = L.geoJSON(null,{pointToLayer : featureToMarkerBackground});
var userMeasurementPointsFill = L.geoJSON(null,{pointToLayer : featureToMarker});
var userMeasurementPoints = L.featureGroup([userMeasurementPointsStroke, userMeasurementPointsFill]);

var allUserMeasurementPointsBounds;
var allUserMeasurementPointsStroke = L.geoJSON(null,{pointToLayer : featureToMarkerBackground});
var allUserMeasurementPointsFill = L.geoJSON(null,{pointToLayer : featureToMarker});
var allUserMeasurementPoints = L.featureGroup([allUserMeasurementPointsStroke, allUserMeasurementPointsFill]);

function addMeasurementPoints(GeoJSONFeatures) {
    userMeasurementPointsStroke.addData(GeoJSONFeatures);
    userMeasurementPointsFill.addData(GeoJSONFeatures);
    map.fitBounds(userMeasurementPoints.getBounds())
}

userMeasurementPoints.addTo(map);

var onomap_tiles = L.tileLayer('https://onomap-gs.noise-planet.org/geoserver/gwc/service/tms/1.0.0/noisecapture:noisecapture_area_laeq@EPSG:900913@png/{z}/{x}/{y}.png', {
tms: true,
zIndex: 2,
maxZoom: 19,
minZoom: 14
});

var onomap_cluster = L.geoJSON.OnoMap('https://onomap-gs.noise-planet.org/geoserver/ows');

var onomap = L.featureGroup([onomap_tiles, onomap_cluster])

var osm = L.tileLayer('https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://osm.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
    zIndex: 1
});

var esriworldimagery = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community',
    maxZoom: 19,
});

var legend = L.control({position: 'bottomleft'});

legend.onAdd = function (map) {

    var div = L.DomUtil.create('div', 'info legend');
    div.innerHTML = "<img src='https://onomap-gs.noise-planet.org/geoserver/gwc/service/wms?REQUEST=GetLegendGraphic&VERSION=1.3.0&FORMAT=image/png&LAYER=noisecapture:noisecapture_area&legend_options=fontName:Cerdana;fontAntiAliasing:true;fontColor:0x000033;fontSize:12;dpi:80&TRANSPARENT=true'/>";
    return div;
};

legend.addTo(map);

osm.addTo(map);

var baseLayers = {
"OpenStreetMap": osm,
"Satellite imagery": esriworldimagery
};

var community_layer_name = "Community sound level (LAeq)"
var all_measurements_layer_name = "All your measurements"
var measurements_layer_name = "Selected measurement";

var overlays = {};

overlays[measurements_layer_name] = userMeasurementPoints;
overlays[all_measurements_layer_name] = allUserMeasurementPoints;
overlays[community_layer_name] = onomap;

// Init supercluster
var index;
var ready = false;


function addAllMeasurementPoints() {
    index = supercluster({
        log: true,
        radius: 60,
        extent: 256,
        maxZoom: 15
    }).load(JSON.parse(androidContent.getAllMeasurementData()));
    ready= true;
    // Compute bounds
    for (var i = 0; i < index.points.length; i++) {
        var point = index.points[i];
        if (!point.geometry) {
            continue;
        }
        var coordinates = point.geometry.coordinates;
        if(typeof allUserMeasurementPointsBounds !== 'undefined') {
            allUserMeasurementPointsBounds.extend(L.latLng(coordinates[1], coordinates[0]))
        } else {
            allUserMeasurementPointsBounds = L.latLngBounds(L.latLng(coordinates[1], coordinates[0]), L.latLng(coordinates[1], coordinates[0]));
        }
    }
    if(typeof allUserMeasurementPointsBounds !== 'undefined') {
        map.flyToBounds(allUserMeasurementPointsBounds);
    }
}

function update() {
    if (!ready) return;
    allUserMeasurementPointsStroke.clearLayers();
    allUserMeasurementPointsFill.clearLayers();
    var bounds = map.getBounds();
    var bbox = [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()]
    var data = index.getClusters(bbox, map.getZoom())
    allUserMeasurementPointsStroke.addData(data);
    allUserMeasurementPointsFill.addData(data);
}


// Returns a function, that, as long as it continues to be invoked, will not
// be triggered. The function will be called after it stops being called for
// N milliseconds. If `immediate` is passed, trigger the function on the
// leading edge, instead of the trailing.
function debounce(func, wait, immediate) {
	var timeout;
	return function() {
		var context = this, args = arguments;
		var later = function() {
			timeout = null;
			if (!immediate) func.apply(context, args);
		};
		var callNow = immediate && !timeout;
		clearTimeout(timeout);
		timeout = setTimeout(later, wait);
		if (callNow) func.apply(context, args);
	};
};

map.on('moveend', debounce(update, 250));


map.on('overlayadd', function(eventLayer){
    if(eventLayer.name === measurements_layer_name) {
        map.fitBounds(userMeasurementPoints.getBounds())
    } else if(eventLayer.name === all_measurements_layer_name) {
        if(!ready) {
            addAllMeasurementPoints();
        } else if(typeof allUserMeasurementPointsBounds !== 'undefined') {
            map.flyToBounds(allUserMeasurementPointsBounds);
        }
    }
});

L.control.layers(baseLayers, overlays).addTo(map);
