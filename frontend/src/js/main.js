mapboxgl.accessToken = 'pk.eyJ1IjoiZ2JpZiIsImEiOiJjaW0xeXU1c3gwMG04dm1tNXB3cjJ3Zm12In0.8A2pUP_lgL19w4G5L0fDNw';

var gb = {};
var key = 1898286;
var minYear = minYear || 1900;
var maxYear = maxYear || 2015;
var mapType = $('#mapType').val();
var hexRadius = $('#hexRadius').val();
var yearThreshold = $('#yearThreshold').val();


/**
 * Initialises the map, taking the year slider into consideration.
 */
function initMap(map) {
    removeStatLayers();
    setStatLayers(key, mapType, hexRadius, yearThreshold);
}

function removeStatLayers() {
    for (var i = 0; i < 11; i++) {
        if (map.getLayer('regression-' + i)) map.removeLayer('regression-' + i);
    }
    if (map.getSource('regression')) map.removeSource('regression');
    if (map.getLayer('regression-fill-hover')) map.removeLayer('regression-fill-hover');
}
function setStatLayers(key, type, hexRadius, yearThreshold) {
    removeStatLayers();
    var colors = [
        '#a50f15',
        '#de2d26',
        '#fb6a4a',
        '#fcae91',
        '#fee5d9',
        '#edf8e9',
        '#bae4b3',
        '#74c476',
        '#31a354',
        '#006d2c'
    ];
    var type = type || 'points';
    var yearThreshold = yearThreshold || 2;
    var hexRadius = hexRadius || 5;
    var sourceName = 'regression';
    var sourceLayerName = type;

    var layerType = type == 'points' ? 'circle' : 'fill';

    var paintFill = {
        "fill-color": "#0F0",
        "fill-opacity": 0.4
    };
    var paintCircle = {
        "circle-radius": 5,
        "circle-color": "#0F0",
        "circle-opacity": 0.5
    };
    var paint = layerType == 'fill' ? paintFill : paintCircle;

    var regressionTiles = 'http://tiletest.gbif.org/' + key + '/{z}/{x}/{y}/' + type + ".pbf?minYear" + minYear + "&maxYear=" + maxYear + "&yearThreshold=" + yearThreshold + "&radius=" + hexRadius;
    map.addSource('regression', {
        type: 'vector',
        "tiles": [regressionTiles]
    });

    if (layerType == 'fill') {
        map.addLayer({
            "id": "regression",
            "type": "line",
            'interactive': true,
            "source": sourceName,
            "source-layer": sourceLayerName,
            "paint": {
                "line-color": "#7b7b7b",
                "line-width": 0.5,
                "line-opacity": 1
            }
        });
    }

    if (layerType == 'circle') {
        map.addLayer({
            "id": "regression",
            "type": "circle",
            'interactive': true,
            "source": sourceName,
            "source-layer": sourceLayerName,
            "paint": {
                "circle-radius": 5,
                "circle-color": "#888",
                "circle-opacity": 0.1
            }
        });
    }

    var breakpoints = colors.reverse().map(function(e, i) {
        return [ (colors.length-6-i)*0.0002, e ];
    });

    breakpoints.forEach(function (layer, i) {
        var paintFill = {
            "fill-color": layer[1],
            "fill-opacity": 0.4
        };
        var paintCircle = {
            "circle-radius": 5,
            "circle-color": layer[1],
            "circle-opacity": 0.4
        };
        var paint = layerType == 'fill' ? paintFill : paintCircle;

        var filter = i == 0 ?
            [">=", "slope", layer[0]] :
            ["all",
                [">=", "slope", layer[0]],
                ["<", "slope", breakpoints[i - 1][0]]];
        if (i==breakpoints.length-1) filter = ["<", "slope", breakpoints[i - 1][0]];


        map.addLayer({
            "id": "regression-" + i,
            "type": layerType,
            'interactive': true,
            "source": sourceName,
            "source-layer": sourceLayerName,
            "paint": paint,
            "filter": filter
        });
    });

    if (layerType == 'fill') {
        //a layer that activates only on a hover over a feature (a cell)
        map.addLayer({
            "id": "regression-fill-hover",
            "type": "fill",
            "source": sourceName,
            "source-layer": sourceLayerName,
            "paint": {
                "fill-color": "#FCA107",
                "fill-opacity": 0.6
            },
            "filter": ['==', "id", ""]
        });
    }
}


var map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/light-v8',
    center: [10, 50],
    zoom: 3,
    bearing: 0,
    pitch: 0,
    maxZoom: 7.9
});
map.addControl(new mapboxgl.Navigation());

map.on('style.load', function () {
    initMap(map);
});


/**
 * Set up the details when a user clicks a cell.
 */
map.on('click', function (e) {
    map.featuresAt(e.point, {
        radius: 3,
        includeGeometry: true,
        layer: 'regression'
    }, function (err, features) {

        if (err || !features.length) {
            return;
        }

        //normalize
        var data = features[0].properties;
        showStats(data);
    });
});

/**
 *  Create the hover over effects on mouse moving.
 */
map.on('mousemove', function (e) {
    if (!map.getLayer('regression-fill-hover')) return;
    map.featuresAt(e.point, {
        radius: 0,
        layer: 'regression'
    }, function (err, features) {
        map.getCanvas().style.cursor = (!err && features.length) ? 'pointer' : '';
        if (!err && features.length) {
            //map.setFilter("coverage-hover", ["==", "id", features[0].properties.id]);
            map.setFilter("regression-fill-hover", ["==", "id", features[0].properties.id]);
        } else {
            //map.setFilter("coverage-hover", ["==", "id", ""]);
            map.setFilter("regression-fill-hover", ["==", "id", ""]);
        }

    });
});






function showStats(data) {

    var labels = [];
    var points = [];
    var normalized = [];
    var line = [];
    for (var i = minYear; i < maxYear; i++) {
        labels.push(i);
        //if (data.hasOwnProperty('' + i)) {
        //    var s = parseInt(data[i]);
        //    var g = parseInt(data[i + '_group']);
        //    var val = s/g;
        //    normalized.push([i, val]);
        //    points.push(val);
        //}
        line.push( data.slope*i + data.intercept );
    }

    var interval = (maxYear-minYear) > 100 ? 25: 10;


    var chart = new Chartist.Line('.ct-chart', {
        labels: labels,
        // Naming the series with the series object array notation
        series: [{
            name: 'line',
            data: line
        }, {
            name: 'points',
            data: points
        }
        ]
    }, {
        fullWidth: true,
        axisX: {
            labelInterpolationFnc: function(value, index) {
                return index % interval === 0 ? value : '';
            },
            showGrid: false
        },
        series: {
            'line': {
                lineSmooth: Chartist.Interpolation.none({
                    fillHoles: true
                }),
                showLine: true,
                showArea: false,
                showPoint: false
            },
            'points': {
                showLine: false,
                showArea: false,
                showPoint: true
            }
        }
    });
}


noUiSlider.create(slider, {
    start: [1900, 2016],
    step: 1,
    connect: true,
    range: {
        'min': 1900,
        'max': 2016 // ok for a pilot project which uses static(!) data
    }
});
slider.noUiSlider.on('update', function (vals) {
    document.getElementById("years").innerText = Math.floor(vals[0]) + " - " + Math.floor(vals[1]);
});
slider.noUiSlider.on('change', function (vals) {
    minYear = Math.floor(vals[0]);
    maxYear = Math.floor(vals[1]);
    initMap(map);
});


function updateMap(select) {
    mapType = select.value;
    if (mapType == 'hex') {
        $('#hexSizeCtrl').show();
    } else {
        $('#hexSizeCtrl').hide();
    }
    initMap(key);
}

function updateYearThreshold(select) {
    yearThreshold = select.value;
    initMap(map);
}

function updateHexThreshold(select) {
    hexRadius = select.value;
    initMap(map);
}
