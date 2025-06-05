
/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

/**
 * Hexagon formulae from :
 * http://www.redblobgames.com/grids/hexagons/
 */
function onomap_constructor(options) {
    this.options = options;
    this._map = options["map"];
    this.server_url = options["server_url"];
    this.wps_url = this.server_url + 'wps';
    this.refreshInterval = null;
    this.last_record_utc = 0;
}

function timeout_onomap(results, status, error) {
// Display info message
    if (status == "timeout") {
        $('#onomap_alert').remove();
        $('#map').append('<div id="onomap_alert" class="alert"> <span class="closebtn" onclick="this.parentElement.style.display=\'none\';">&times;</span>  <strong>Notice</strong><br/>We apologize, the NoiseCapture server is currently not accessible, please refresh the web page later, thank you.</div>');
    }
}

var onomap_class = {
    COLOR_RAMP: {
        30: "#82A6AD",
        35: "#A0BABF",
        40: "#B8D6D1",
        45: "#CEE4CC",
        50: "#E2F2BF",
        55: "#F3C683",
        60: "#E87E4D",
        65: "#CD463E",
        70: "#A11A4D",
        75: "#75085C",
        80: "#430A4A"
    },
    hexOverlay: null,
    countries: {
        "Afghanistan": "AF",
        "Åland Islands": "AX",
        "Albania": "AL",
        "Algeria": "DZ",
        "American Samoa": "AS",
        "Andorra": "AD",
        "Angola": "AO",
        "Anguilla": "AI",
        "Antarctica": "AQ",
        "Antigua and Barbuda": "AG",
        "Argentina": "AR",
        "Armenia": "AM",
        "Aruba": "AW",
        "Australia": "AU",
        "Austria": "AT",
        "Azerbaijan": "AZ",
        "Bahamas": "BS",
        "Bahrain": "BH",
        "Bangladesh": "BD",
        "Barbados": "BB",
        "Belarus": "BY",
        "Belgium": "BE",
        "Belize": "BZ",
        "Benin": "BJ",
        "Bermuda": "BM",
        "Bhutan": "BT",
        "Bolivia, Plurinational State of": "BO",
        "Bonaire, Sint Eustatius and Saba": "BQ",
        "Bosnia and Herzegovina": "BA",
        "Botswana": "BW",
        "Bouvet Island": "BV",
        "Brazil": "BR",
        "British Indian Ocean Territory": "IO",
        "Brunei Darussalam": "BN",
        "Bulgaria": "BG",
        "Burkina Faso": "BF",
        "Burundi": "BI",
        "Cambodia": "KH",
        "Cameroon": "CM",
        "Canada": "CA",
        "Cape Verde": "CV",
        "Cayman Islands": "KY",
        "Central African Republic": "CF",
        "Chad": "TD",
        "Chile": "CL",
        "China": "CN",
        "Christmas Island": "CX",
        "Cocos (Keeling) Islands": "CC",
        "Colombia": "CO",
        "Comoros": "KM",
        "Congo": "CG",
        "Congo, the Democratic Republic of the": "CD",
        "Cook Islands": "CK",
        "Costa Rica": "CR",
        "Côte d'Ivoire": "CI",
        "Croatia": "HR",
        "Cuba": "CU",
        "Curaçao": "CW",
        "Cyprus": "CY",
        "Czech Republic": "CZ",
        "Denmark": "DK",
        "Djibouti": "DJ",
        "Dominica": "DM",
        "Dominican Republic": "DO",
        "Ecuador": "EC",
        "Egypt": "EG",
        "El Salvador": "SV",
        "Equatorial Guinea": "GQ",
        "Eritrea": "ER",
        "Estonia": "EE",
        "Ethiopia": "ET",
        "Falkland Islands (Malvinas)": "FK",
        "Faroe Islands": "FO",
        "Fiji": "FJ",
        "Finland": "FI",
        "France": "FR",
        "French Guiana": "GF",
        "French Polynesia": "PF",
        "French Southern Territories": "TF",
        "Gabon": "GA",
        "Gambia": "GM",
        "Georgia": "GE",
        "Germany": "DE",
        "Ghana": "GH",
        "Gibraltar": "GI",
        "Greece": "GR",
        "Greenland": "GL",
        "Grenada": "GD",
        "Guadeloupe": "GP",
        "Guam": "GU",
        "Guatemala": "GT",
        "Guernsey": "GG",
        "Guinea": "GN",
        "Guinea-Bissau": "GW",
        "Guyana": "GY",
        "Haiti": "HT",
        "Heard Island and McDonald Mcdonald Islands": "HM",
        "Holy See (Vatican City State)": "VA",
        "Honduras": "HN",
        "Hong Kong": "HK",
        "Hungary": "HU",
        "Iceland": "IS",
        "India": "IN",
        "Indonesia": "ID",
        "Iran": "IR",
        "Iraq": "IQ",
        "Ireland": "IE",
        "Isle of Man": "IM",
        "Israel": "IL",
        "Italy": "IT",
        "Jamaica": "JM",
        "Japan": "JP",
        "Jersey": "JE",
        "Jordan": "JO",
        "Kazakhstan": "KZ",
        "Kenya": "KE",
        "Kiribati": "KI",
        "North Korea": "KP",
        "South Korea": "KR",
        "Kuwait": "KW",
        "Kyrgyzstan": "KG",
        "Lao People's Democratic Republic": "LA",
        "Latvia": "LV",
        "Lebanon": "LB",
        "Lesotho": "LS",
        "Liberia": "LR",
        "Libya": "LY",
        "Liechtenstein": "LI",
        "Lithuania": "LT",
        "Luxembourg": "LU",
        "Macao": "MO",
        "Macedonia, the Former Yugoslav Republic of": "MK",
        "Madagascar": "MG",
        "Malawi": "MW",
        "Malaysia": "MY",
        "Maldives": "MV",
        "Mali": "ML",
        "Malta": "MT",
        "Marshall Islands": "MH",
        "Martinique": "MQ",
        "Mauritania": "MR",
        "Mauritius": "MU",
        "Mayotte": "YT",
        "Mexico": "MX",
        "Micronesia, Federated States of": "FM",
        "Moldova, Republic of": "MD",
        "Monaco": "MC",
        "Mongolia": "MN",
        "Montenegro": "ME",
        "Montserrat": "MS",
        "Morocco": "MA",
        "Mozambique": "MZ",
        "Myanmar": "MM",
        "Namibia": "NA",
        "Nauru": "NR",
        "Nepal": "NP",
        "Netherlands": "NL",
        "New Caledonia": "NC",
        "New Zealand": "NZ",
        "Nicaragua": "NI",
        "Niger": "NE",
        "Nigeria": "NG",
        "Niue": "NU",
        "Norfolk Island": "NF",
        "Northern Mariana Islands": "MP",
        "Norway": "NO",
        "Oman": "OM",
        "Pakistan": "PK",
        "Palau": "PW",
        "Palestine, State of": "PS",
        "Panama": "PA",
        "Papua New Guinea": "PG",
        "Paraguay": "PY",
        "Peru": "PE",
        "Philippines": "PH",
        "Pitcairn": "PN",
        "Poland": "PL",
        "Portugal": "PT",
        "Puerto Rico": "PR",
        "Qatar": "QA",
        "Réunion": "RE",
        "Romania": "RO",
        "Russian Federation": "RU",
        "Rwanda": "RW",
        "Saint Barthélemy": "BL",
        "Saint Helena, Ascension and Tristan da Cunha": "SH",
        "Saint Kitts and Nevis": "KN",
        "Saint Lucia": "LC",
        "Saint Martin (French part)": "MF",
        "Saint Pierre and Miquelon": "PM",
        "Saint Vincent and the Grenadines": "VC",
        "Samoa": "WS",
        "San Marino": "SM",
        "Sao Tome and Principe": "ST",
        "Saudi Arabia": "SA",
        "Senegal": "SN",
        "Serbia": "RS",
        "Seychelles": "SC",
        "Sierra Leone": "SL",
        "Singapore": "SG",
        "Sint Maarten (Dutch part)": "SX",
        "Slovakia": "SK",
        "Slovenia": "SI",
        "Solomon Islands": "SB",
        "Somalia": "SO",
        "South Africa": "ZA",
        "South Georgia and the South Sandwich Islands": "GS",
        "South Sudan": "SS",
        "Spain": "ES",
        "Sri Lanka": "LK",
        "Sudan": "SD",
        "Suriname": "SR",
        "Svalbard and Jan Mayen": "SJ",
        "Swaziland": "SZ",
        "Sweden": "SE",
        "Switzerland": "CH",
        "Syrian Arab Republic": "SY",
        "Taiwan": "TW",
        "Tajikistan": "TJ",
        "Tanzania": "TZ",
        "Thailand": "TH",
        "Timor-Leste": "TL",
        "Togo": "TG",
        "Tokelau": "TK",
        "Tonga": "TO",
        "Trinidad and Tobago": "TT",
        "Tunisia": "TN",
        "Turkey": "TR",
        "Turkmenistan": "TM",
        "Turks and Caicos Islands": "TC",
        "Tuvalu": "TV",
        "Uganda": "UG",
        "Ukraine": "UA",
        "United Arab Emirates": "AE",
        "United Kingdom": "GB",
        "United States": "US",
        "United States Minor Outlying Islands": "UM",
        "Uruguay": "UY",
        "Uzbekistan": "UZ",
        "Vanuatu": "VU",
        "Venezuela, Bolivarian Republic of": "VE",
        "Viet Nam": "VN",
        "Virgin Islands, British": "VG",
        "Virgin Islands, U.S.": "VI",
        "Wallis and Futuna": "WF",
        "Western Sahara": "EH",
        "Yemen": "YE",
        "Zambia": "ZM",
        "Zimbabwe": "ZW"
    },
    // Hex size
    size: 15.,
    // While moving the mouse do not redraw if hex coordinate is the same
    lastDrawnHex: {q: 0, r: 0},
    // Last hexa donut downloaded data
    data: null,
    // selected NoiseCapture party
    partyData: null,
    // Last history data
    data_histo: null,
    // Start stop marker of selected history
    start_stop_layer: null,
    // Chart.js instances
    timeCharts: {},


    /**
     * @param hex Hex index
     * @return Local coordinate of hexagon index
     */
    hex_to_meter: function (hex) {
        x = this.size * Math.sqrt(3.) * (hex.q + hex.r / 2.);
        y = this.size * 3. / 2. * hex.r;
        return {x: x, y: y};
    },

    loadNoiseCaptureParty: function (partyData) {
        this.partyData = partyData;
    },

    /**
     * @param center Hex center position
     * @param size Hex size
     * @param i Vertex [0-5]
     * @return Vertex coordinate
     */
    hex_corner: function (center, size, i) {
        var angle_deg = 60. * i + 30.;
        var angle_rad = Math.PI / 180. * angle_deg;
        return {x: center.x + size * Math.cos(angle_rad), y: center.y + size * Math.sin(angle_rad)};
    },

    /**
     * @param hex Hex index
     * @return x,y,z cube index
     */
    hex_to_cube: function (h) {
        var x = h.q
        var z = h.r
        var y = -x - z
        return {x: x, y: y, z: z}
    },

    /**
     * @param h Cube index
     * @return q,r hex index
     */
    cube_to_hex: function (h) {
        var q = h.x
        var r = h.z
        return {q: q, r: r}
    },

    /**
     * @param h aproximate cube index
     * @return h Cube index
     */
    cube_round: function (h) {
        var rx = Math.round(h.x);
        var ry = Math.round(h.y);
        var rz = Math.round(h.z);

        var x_diff = Math.abs(rx - h.x);
        var y_diff = Math.abs(ry - h.y);
        var z_diff = Math.abs(rz - h.z);

        if (x_diff > y_diff && x_diff > z_diff)
            rx = -ry - rz
        else if (y_diff > z_diff)
            ry = -rx - rz
        else
            rz = -rx - ry

        return {x: rx, y: ry, z: rz}
    },

    /**
     * @param h aprox hex index
     * @return h hex index
     */
    hex_round: function (h) {
        return this.cube_to_hex(this.cube_round(this.hex_to_cube(h)));
    },

    /**
     * @param x x coordinate
     * @param y y coordinate
     * @return q,r hex index
     */
    meterToHex: function (x, y) {
        var q = (x * Math.sqrt(3.) / 3. - y / 3.) / this.size;
        var r = y * 2. / 3. / this.size;
        return this.hex_round({q: q, r: r});
    },

    /**
     *    Mousemove callback function updating labels and input elements
     */
    updateHexOverlay: function (evt) {
        if (map.getZoom() < 15) {
            if (this.hexOverlay != null) {
                this.hexOverlay.removeFrom(map);
            }
            return;
        }
        var pos = evt.latlng,
            opts = this.options;
        if (pos) {
            pos = pos.wrap();
            var gPos = proj4('EPSG:3857', [pos.lng, pos.lat]);
            var hPos = this.meterToHex(gPos[0], gPos[1]);
            if (this.lastDrawnHex.q != hPos.q || this.lastDrawnHex.r != hPos.r) {
                //console.log("EPSG:3857 = " + gPos + "; EPSG:4326 = " + pos)
                if (this.hexOverlay) {
                    map.removeLayer(this.hexOverlay)
                }
                this.lastDrawnHex = hPos;
                // Compute corners of hex
                var center = this.hex_to_meter(hPos)
                var vertices = []
                for (i = 0; i < 6; i++) {
                    var v = this.hex_corner(center, this.size, i);

                    var vGPos = proj4('EPSG:3857', 'EPSG:4326', [v.x, v.y]);

                    if (isNaN(vGPos[0]) || isNaN(vGPos[1])) {
                        return;
                    }
                    vertices.push(new L.LatLng(vGPos[1], vGPos[0]));
                }
                // Close the ring
                vertices.push(vertices[0]);
                this.hexOverlay = new L.Polyline(vertices, {
                    color: 'gray',
                    interactive: false,
                    weight: 3,
                    opacity: 0.5,
                    smoothFactor: 1
                });
                this.hexOverlay.addTo(map);
            }
        }
    },

    onSelectLang: function (target) {
        $('#time_lang')[0].setAttribute("lang", target.attributes.lang.value);
        if (this.data) {
            this.showGetFeatureInfo(null, null, this.data);
        }
    },

    onSelectTimeOption: function (target) {
        if (this.data) {
            this.showGetFeatureInfo(null, null, this.data);
        }
    },

    setAutoTrack: function (newValue) {
        this.options["doAutoFly"] = newValue;
        var thisgetHistory = L.Util.bind(this.getHistory, this);
        if (newValue) {
            this.refreshInterval = setInterval(thisgetHistory, 35000);
            this.getHistory();
        } else {
            clearInterval(this.refreshInterval);
        }
    },

    onAdd: function (map) {
        // Fetch language selection
        var _this = this;
        map.on('click', this.getFeatureInfo, this);
        map.on("mousemove", this.updateHexOverlay, this);
        //var histFunc = L.Util.bind(this.getHistory, this);
        $('#time_lang_list span').on('click', function (element) {
            _this.onSelectLang(element.target)
        });
        // Fetch time option
        $("input[name='tz-option']").on('click', function (element) {
            _this.onSelectTimeOption(element.target)
        });
        ;
        // Load default language
        if (navigator.language) {
            $('#time_lang')[0].setAttribute("lang", navigator.language);
        }
    },

    onRemove: function (map) {
        map.off('click', this.getFeatureInfo, this);
        map.off("mousemove", this.updateHexOverlay, this);
    },

    showHistory: function (data) {
        if (data instanceof Array) {
            this.data_histo = data;
            var histoDiv = document.getElementById('measures_log');
            var lang = $('#time_lang')[0].attributes.lang.value;
            moment.locale(lang);
            var onsite = $('input[name=tz-option]:checked')[0].attributes.onsite;
            html_cont = "<table class=\"table\"><thead><tr><th>Goto</th><th>Date</th><th>Length</th></tr></thead><tbody>";
            for (i = 0; i < data.length; i++) {
                var row = data[i];
                //#15/47.6574/-2.9206
                // time_length":1,"record_utc":"2016-09-30T11:06:22.000+0200","zoom_level":18,"lat":47.1540551,"long":-1.6454225},{"tim
                var time_record;
                if (onsite) {
                    // User check to see the time on the measurement zone (not on browser timezone)
                    time_record = moment.parseZone(row.record_utc).format('LLL');
                } else {
                    // User want to see in local time
                    time_record = moment(row.record_utc).format('LLL');
                }
                var location_title = row.name_3 + ", " + row.name_1 + ", " + row.country;
                var country_iso = '';
                if (row.country in this.countries) {
                    country_iso = "flag-icon-" + this.countries[row.country].toLowerCase();
                }
                html_cont += "<tr><td><a data-placement=\"right\"  style=\"cursor: pointer;\" data-toggle=\"tooltip\" title=\"" + location_title + "\" onclick=\"onomap.goToHistory(" + i + ")\"><span class=\"flag-icon " + country_iso + "\"></span></a></td><td>" + time_record + "</td><td>" + row.time_length + " s</td></tr>";
            }
            html_cont += "</tbody></table>";
            histoDiv.innerHTML = html_cont;
            $('[data-toggle="tooltip"]').tooltip();
            // Fly to last track
            if (this.options["doAutoFly"] && data.length > 0 && data[0].record_utc != this.last_record_utc) {
                this.last_record_utc = data[0].record_utc;
                this.goToHistory(0);
            }
        }
    },

    goToHistory: function (historyId) {
        var row = this.data_histo[historyId];
        if (row.bounds.type == 'Point') {
            var bounds = row.bounds.coordinates;
            if (!this.options["doAutoFly"]) {
                map.setView([bounds[1], bounds[0]], 18);
            } else {
                map.flyTo([bounds[1], bounds[0]], 18);
            }
        } else {
            var bounds = row.bounds.coordinates[0];
            if (!this.options["doAutoFly"]) {
                map.fitBounds([
                    [bounds[0][1], bounds[0][0]],
                    [bounds[2][1], bounds[2][0]]
                ]);
            } else {
                map.flyToBounds([
                    [bounds[0][1], bounds[0][0]],
                    [bounds[2][1], bounds[2][0]]
                ]);
            }
        }
        // Add start stop layer
        if (this.start_stop_layer) {
            this.start_stop_layer.clearLayers();
        } else {
            this.start_stop_layer = L.featureGroup([]);
            this.start_stop_layer.addTo(map);
        }
        var startFlag = row.start.coordinates;
        var stopFlag = row.stop.coordinates;
        this.start_stop_layer.addLayer(L.marker([startFlag[1], startFlag[0]], {
            icon: L.AwesomeMarkers.icon({
                icon: 'flag-o',
                prefix: 'fa',
                markerColor: 'green',
                iconColor: 'black'
            })
        }));
        this.start_stop_layer.addLayer(L.marker([stopFlag[1], stopFlag[0]], {
            icon: L.AwesomeMarkers.icon({
                icon: 'flag-checkered',
                prefix: 'fa',
                markerColor: 'black',
                iconColor: 'white'
            })
        }));
        if (this.options["doAutoFly"]) {
            // Redraw tiles in some seconds, as processing may have be done yet
            setTimeout(function () {
                map.eachLayer(function (layer) {
                    if (typeof layer.redraw == 'function') {
                        layer.redraw();
                    }
                });
            }, 10000);
        }
    },


    doFlyToLastMeasure: function (data) {
        if (data instanceof Array && data.length > 0) {
            this.data_histo = data;
            this.goToHistory(0);
        }
    },

    flyToLastMeasure: function () {
        // Make an AJAX request to the server and hope for the best
        var url = this.getFeatureInfoUrl('groovy:nc_last_measures'),
            doFlyToLastMeasure = L.Util.bind(this.doFlyToLastMeasure, this);
        var _this = this;
        var postData = this.getHistoryContent(this.partyData != null ? this.partyData.pk_party : "");
        $.ajax({
            type: 'POST',
            crossDomain: true,
            data: postData,
            contentType: "text/plain",
            dataType: "json",
            url: url,
            success: function (data, status, xhr) {
                doFlyToLastMeasure(data);
            },
            error: function (xhr, status, error) {
                showResults(error);
            }
        });
        // Clean markers
        if (this.start_stop_layer && !this.options["doAutoFly"]) {
            this.start_stop_layer.clearLayers();
        }
    },

    getHistory: function () {
        // Make an AJAX request to the server and hope for the best
        var url = this.getFeatureInfoUrl('groovy:nc_last_measures'),
            showResults = L.Util.bind(this.showHistory, this);
        var _this = this;
        var postData = this.getHistoryContent(this.partyData != null ? this.partyData.pk_party : "");
        $.ajax({
            type: 'POST',
            crossDomain: true,
            data: postData,
            contentType: "text/plain",
            dataType: "json",
            url: url,
            success: function (data, status, xhr) {
                if (data instanceof Array) {
                    _this.histodata = data;
                }
                showResults(_this.histodata);
            },
            error: function (xhr, status, error) {
                showResults(error);
            }
        });
        // Clean markers
        if (this.start_stop_layer && !this.options["doAutoFly"]) {
            this.start_stop_layer.clearLayers();
        }
    },

    getFeatureInfo: function (evt) {
        // Make an AJAX request to the server and hope for the best
        var url = this.getFeatureInfoUrl(),
            showResults = L.Util.bind(this.showGetFeatureInfo, this);
        var postData = this.getFeatureInfoContent(evt.latlng);
        var _this = this;
        $.ajax({
            type: 'POST',
            crossDomain: true,
            data: postData,
            contentType: "text/plain",
            dataType: "json",
            url: url,
            success: function (data, status, xhr) {
                var err = data["la50"] != null ? null : data;
                if (data["la50"] != null) {
                    _this.data = data;
                }
                showResults(err, evt.latlng, data);
            },
            error: function (xhr, status, error) {
                showResults(error);
            }
        });
    },

    getFeatureInfoUrl: function (funcIdentifier) {
        // Construct a GetFeatureInfo request URL
        size = this._map.getSize(),
            params = {
                request: 'Execute',
                service: 'wps',
                version: '1.0.0'
            };
        if (funcIdentifier) {
            params['Identifier'] = funcIdentifier;
        }
        return this.wps_url + L.Util.getParamString(params, this.wps_url, true);
    },

    getHistoryContent: function (noise_party_id) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wps:Execute version=\"1.0.0\" \
     service=\"WPS\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  \
     xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\"  \
     xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\"  \
     xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" \
      xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"  \
      xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\"> \
      <ows:Identifier>groovy:nc_last_measures</ows:Identifier> \
      <wps:DataInputs>\
        <wps:Input>\
          <ows:Identifier>noiseparty</ows:Identifier>\
          <wps:Data>\
            <wps:LiteralData>" + noise_party_id + "</wps:LiteralData>\
          </wps:Data>\
        </wps:Input>\
      </wps:DataInputs>\
  <wps:ResponseForm> \
    <wps:RawDataOutput> \
      <ows:Identifier>result</ows:Identifier> \
    </wps:RawDataOutput> \
  </wps:ResponseForm> \
</wps:Execute>";
    },
    getFeatureInfoContent: function (latlng) {
        // Construct a GetFeatureInfo request content given a point
        if (latlng) {
            var gPos = proj4('EPSG:3857', [latlng.lng, latlng.lat]);
            var partyId = null;
            if (this.partyData != null) {
                partyId = this.partyData.pk_party;
            }
            var hPos = this.meterToHex(gPos[0], gPos[1]);
            var qIndex = hPos.q;
            var rIndex = hPos.r;
            var data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wps:Execute version=\"1.0.0\" service=\"WPS\"\
             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.opengis.net/wps/1.0.0\"\
              xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" \
              xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:gml=\"http://www.opengis.net/gml\" \
              xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" \
              xmlns:xlink=\"http://www.w3.org/1999/xlink\" \
              xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\
          <ows:Identifier>groovy:nc_get_area_info</ows:Identifier>\
          <wps:DataInputs>\
            <wps:Input>\
              <ows:Identifier>rIndex</ows:Identifier>\
              <wps:Data>\
                <wps:LiteralData>" + rIndex + "</wps:LiteralData>\
              </wps:Data>\
            </wps:Input>\
            <wps:Input>\
              <ows:Identifier>qIndex</ows:Identifier>\
              <wps:Data>\
                <wps:LiteralData>" + qIndex + "</wps:LiteralData>\
              </wps:Data>\
            </wps:Input>\
            <wps:Input>\
              <ows:Identifier>noiseparty</ows:Identifier>\
              <wps:Data>\
                <wps:LiteralData>" + partyId + "</wps:LiteralData>\
              </wps:Data>\
            </wps:Input>\
          </wps:DataInputs>\
          <wps:ResponseForm>\
            <wps:RawDataOutput>\
              <ows:Identifier>result</ows:Identifier>\
            </wps:RawDataOutput>\
          </wps:ResponseForm>\
        </wps:Execute>";
            return data;
        } else {
            return ""
        }
    },

    loadHourlyGraphData: function (canvas_period, data) {
        var labels = ["0h", "1h", "2h", "3h", "4h", "5h", "6h", "7h", "8h", "9h",
            "10h", "11h", "12h", "13h", "14h", "15h", "16h", "17h", "18h", "19h",
            "20h", "21h", "22h", "23h"];
        for (var i = 0; i < labels.length; i++) {
            labels[i] = Transifex.live.translateText(labels[i]);
        }
        var graphs = {
            'la50': document.getElementById('statistics_' + canvas_period + '_la50'),
            laeq: document.getElementById('statistics_' + canvas_period + '_laeq')
        };
        var graphsLabels = {'la50': 'LA50', 'laeq': 'LAeq'};
        for (var graphCat in graphs) {
            var graphEl = graphs[graphCat];
            if (graphEl != null) {
                var dataset = [];
                var colorset = [];
                for (var hour = 0; hour < 24; hour++) {
                    var hourDict = data[hour];
                    if (hourDict != null) {
                        dataset.push(hourDict[graphCat]);
                        colorset.push(this.getColor(hourDict[graphCat]));
                    } else {
                        dataset.push(null);
                        colorset.push("white");
                    }
                }
                var chartIdentifier = canvas_period + "_" + graphCat;
                // Check if chart object is already created
                if (this.timeCharts[chartIdentifier] != null) {
                    // Update data
                    this.timeCharts[chartIdentifier].data.datasets[0].data = dataset;
                    this.timeCharts[chartIdentifier].data.datasets[0].backgroundColor = colorset;
                    this.timeCharts[chartIdentifier].update();
                } else {
                    this.timeCharts[chartIdentifier] = new Chart(graphEl, {
                        type: 'bar',
                        data: {
                            labels: labels,
                            datasets: [
                                {
                                    label: Transifex.live.translateText(graphsLabels[graphCat]),
                                    backgroundColor: colorset,
                                    borderColor: "#67a9cf",
                                    data: dataset
                                }
                            ]
                        },
                        options: {
                            legend: {display: false},
                            scales: {
                                yAxes: [{
                                    display: true,
                                    ticks: {
                                        min: 30,   // minimum value will be 0.
                                        suggestedMax: 80,
                                        stepSize: 5
                                    }
                                }]
                            },
                            tooltips: {
                                callbacks: {
                                    label: function (tooltipItem, data) {
                                        if (isNaN(tooltipItem.yLabel)) {
                                            return null;
                                        } else {
                                            return data.datasets[tooltipItem.datasetIndex].label + ': ' + tooltipItem.yLabel.toFixed(1) + ' dB(A)';
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
                // Change color of bars

            }
        }
    },

    getColor: function (level) {
        var prev = -1;
        var i;
        for (i in this.COLOR_RAMP) {
            var n = parseInt(i);
            if ((prev != -1) && (level < n))
                return this.COLOR_RAMP[prev];
            else
                prev = n;
        }
        var levels = Object.keys(this.COLOR_RAMP);
        return this.COLOR_RAMP[levels[levels.length - 1]];
    },

    loadUserRanksTable: function (rankDiv, users) {
        if (users instanceof Array && rankDiv != null) {
            html_cont = "<table class=\"table table-striped\"><thead><tr><th>" + Transifex.live.translateText("Rank") + "</th><th>" + Transifex.live.translateText("Device Id") + "</th><th>" + Transifex.live.translateText("Number of measurements") + "</th><th>" + Transifex.live.translateText("Total duration") + "</th></tr></thead><tbody>";
            for (i = 0; i < users.length; i++) {
                var row = users[i];
                html_cont += "<tr><td>" + (i + 1) + "</td><td><a data-toggle=\"tooltip\" data-placement=\"right\" title=\"" + row.userid + "\"><span class=\"label label-default\">" + row.userid.substring(0, 4) + "</span></a></td><td>" + row.total_records + "</td><td>" + length_to_dd_hh_mm_ss(row.total_length) + "</td></tr>";
            }
            html_cont += "</tbody></table>";
            rankDiv.innerHTML = html_cont;
            $('[data-toggle="tooltip"]').tooltip();
        }
    },

    showGetFeatureInfo: function (err, latlng, content) {
        if (err) {
            console.log(err);
            return;
        } // do nothing if there's an error
        var infoDiv = document.getElementById('areainfo');

        var lang = $('#time_lang')[0].attributes.lang.value;
        moment.locale(lang);
        var first_measure;
        var last_measure;
        $("#area_tags").find('span').remove();
        if (typeof content !== 'undefined' && content["first_measure"]) {
            if ($('input[name=tz-option]:checked')[0].attributes.onsite) {
                // User check to see the time on the measurement zone (not on browser timezone)
                first_measure = moment.parseZone(content["first_measure"]).format('LLL');
                last_measure = moment.parseZone(content["last_measure"]).format('LLL');
            } else {
                // User want to see in local time
                first_measure = moment(content["first_measure"]).format('LLL');
                last_measure = moment(content["last_measure"]).format('LLL');
            }
        } else {
            first_measure = "None";
            last_measure = "None";
        }
        infoDiv.innerHTML = "<img style=\"margin:5px 5px; float:right;\" src=\"" + this.server_url + "gwc/service/wms?REQUEST=GetLegendGraphic&VERSION=1.3.0&FORMAT=image/png&LAYER=noisecapture:noisecapture_area&legend_options=fontName:Cerdana;fontAntiAliasing:true;fontColor:0x000033;fontSize:12;dpi:80&TRANSPARENT=true\"/>\
    <p class='attribute_label'>LA50:</p><i class='fa fa-microphone' aria-hidden='true'></i> " + (content["la50"] ? content["la50"].toFixed(1) + " dB(A)" : "None") + "\
    <p class='attribute_label'>LAeq:</p><i class='fa fa-microphone' aria-hidden='true'></i> " + (content["laeq"] ? content["laeq"].toFixed(1) + " dB(A)" : "None") + "\
    <p class='attribute_label'>First measure:</p><i class='fa fa-clock-o' aria-hidden='true'></i> " + first_measure + "\
    <p class='attribute_label'>Last measure:</p><i class='fa fa-clock-o' aria-hidden='true'></i> " + last_measure + "\
    <p class='attribute_label'>Pleasantness:</p><i class='fa fa-smile-o' aria-hidden='true'></i> " + (content["mean_pleasantness"] ? Math.round(content["mean_pleasantness"]) + " %" : "NC") + "\
    <p class='attribute_label'>Measure duration:</p><i class='fa fa-hourglass' aria-hidden='true'></i> " + (content["measure_count"] ? Math.round(content["measure_count"]) + " seconds" : "None");

        // Split hour levels for week, saturday and sunday
        if (typeof content !== 'undefined') {
            var alldata = content["profile"];
            var weekData = alldata;
            var saturdayData = [];
            var sundayData = [];
            if (alldata) {
                for (i = 0; i < 24; i++) {
                    var key = i.toString();
                    saturdayData.push(alldata[24 + i]);
                    sundayData.push(alldata[48 + i]);
                }
            }
            if (typeof weekData !== 'undefined') {
                // Load graph later as it may take time to compute graph the first time
                var loadHourlyGraphDataWorking_day = L.Util.bind(this.loadHourlyGraphData, this, 'working_day', weekData);
                setTimeout(loadHourlyGraphDataWorking_day, 50);
                var loadHourlyGraphDataSaturday = L.Util.bind(this.loadHourlyGraphData, this, 'saturday', saturdayData);
                setTimeout(loadHourlyGraphDataSaturday, 50);
                var loadHourlyGraphDataSunday = L.Util.bind(this.loadHourlyGraphData, this, 'sunday', sundayData);
                setTimeout(loadHourlyGraphDataSunday, 50);
            }
        }
        // Load tags
        if (typeof content !== 'undefined' && "tags" in content) {
            for (var idTag = 0; idTag < content["tags"].length; idTag++) {
                content["tags"][idTag]["html"] = {
                    'data-original-title': Transifex.live.translateText("{nbseconds} seconds", {nbseconds: content["tags"][idTag]["weight"]}),
                    'data-toggle': "tooltip",
                    'data-placement': "top",
                    title: ""
                }
                content["tags"][idTag]["text"] = Transifex.live.translateText(content["tags"][idTag]["text"]);
            }
            $("#area_tags").jQCloud(content["tags"]);
        }

        // Have to call this for tags clouds toolips to work
        // do not remove
        setTimeout(function () {
            $('[data-toggle="tooltip"]').tooltip();
        }, 1000);
        if (alldata) {
            sidebar.open('hexainfo');
        }
    }
};


createOnomap = function (options) {
    return jQuery.extend({}, new onomap_constructor(options), onomap_class);
};
