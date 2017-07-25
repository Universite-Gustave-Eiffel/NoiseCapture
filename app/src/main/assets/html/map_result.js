
var map = L.map('map').fitWorld();

L.control.scale({
    'position': 'bottomright',
    'metric': true,
    'imperial': false
}).addTo(map);

function featureToMarker(feature, latlng) {
        return L.circleMarker(latlng, {
            color: '#ffffff',
            fillColor: feature.properties["marker-color"],
            weight: 0.,
            fillOpacity: 1.,
            radius: 2,
            stroke: false,
            zIndex: 4
        });
}

var userMeasurementPoints = L.geoJSON(null,{pointToLayer : featureToMarker});

function addMeasurementPoints(GeoJSONFeatures) {
    userMeasurementPoints.addData(GeoJSONFeatures);
}

userMeasurementPoints.addTo(map);

var onomap = L.tileLayer('http://onomap-gs.noise-planet.org/geoserver/gwc/service/tms/1.0.0/noisecapture:noisecapture_area@EPSG:900913@png/{z}/{x}/{y}.png', {
"attribution": "<a href='http://onomap-gs.noise-planet.org'>OnoMap server</a>",
tms: true,
zIndex: 2,
minZoom: 14
});

var osm = L.tileLayer('http://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
    zIndex: 1
});

var legend = L.control({position: 'bottomleft'});

legend.onAdd = function (map) {

    var div = L.DomUtil.create('div', 'info legend');
    div.innerHTML = "<img src='http://onomap-gs.noise-planet.org/geoserver/gwc/service/wms?REQUEST=GetLegendGraphic&VERSION=1.3.0&FORMAT=image/png&LAYER=noisecapture:noisecapture_area&legend_options=fontName:Cerdana;fontAntiAliasing:true;fontColor:0x000033;fontSize:12;dpi:80&TRANSPARENT=true'/>";
    return div;
};

legend.addTo(map);

osm.addTo(map);

var baseLayers = {
"OpenStreetMap": osm
};

var community_layer_name = "Community sound level (LA50)"
var measurements_layer_name = "Measurements";

var overlays = {};

overlays[measurements_layer_name] = userMeasurementPoints;
overlays[community_layer_name] = onomap;

map.on('overlayadd', function(eventLayer){
    if(eventLayer.name === measurements_layer_name) {
        map.flyToBounds(userMeasurementPoints.getBounds())
    }
});

L.control.layers(baseLayers, overlays).addTo(map);
