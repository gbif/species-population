mapboxgl.accessToken = 'pk.eyJ1IjoiZ2JpZiIsImEiOiJjaW0xeXU1c3gwMG04dm1tNXB3cjJ3Zm12In0.8A2pUP_lgL19w4G5L0fDNw';

var gb = {};
var key = 1898286;
var datasetLayer = 'http://tiletest.gbif.org/' + key + '/{z}/{x}/{y}.pbf';
//var datasetLayer = 'http://localhost:3002/api/{z}/{x}/{y}.pbf';

var occurrenceLayer = "http://api.gbif.org/v1/map/density/tile?x={x}&y={y}&z={z}&palette=reds&resolution=2&type=TAXON&key=" + key;

/**
 * Initialises the map, taking the year slider into consideration.
 */
function initMap(map) {
    // Remove layers if they exist
    if (map.getSource('datasets')) map.removeSource('datasets');
    if (map.getSource('occurrence')) map.removeSource('occurrence');
    if (map.getLayer('coverage')) map.removeLayer('coverage');
    if (map.getLayer('coverage-hover')) map.removeLayer('coverage-hover');
    if (map.getLayer('coverage-hover-fill')) map.removeLayer('coverage-hover-fill');
    if (map.getLayer('occurrence-tiles')) map.removeLayer('occurrence-tiles');

    map.addSource('datasets', {
        type: 'vector',
        "tiles": [datasetLayer]
    });
    map.addSource('occurrence', {
        type: 'raster',
        "tiles": [occurrenceLayer],
        "tileSize": 256
    });

    //occurrence layer
    map.addLayer({
        "id": "occurrence-tiles",
        "type": "raster",
        "source": "occurrence"
    });

    map.addLayer({
        "id": "statistics",
        "type": "line",
        'interactive': true,
        "source": "datasets",
        "source-layer": "statistics",
        "paint": {
            "line-color": "#7b7b7b",
            "line-opacity": 1,
            "line-width": 1,
            "line-blur": 1
        }
    });
    addStatLayers();

    map.addSource('test', {
        type: 'geojson',
        "data": sampleAreaGeojson
    });

    map.addLayer({
        "id": "testLayer",
        "type": "geojson",
        "source": "test"
    });

    // a layer that activates only on a hover over a feature (a cell)
    map.addLayer({
        "id": "coverage-hover",
        "type": "line",
        "source": "datasets",
        "source-layer": "statistics",
        "layout": {},
        "paint": {
            "line-color": "#7b7b7b",
            "line-opacity": 1,
            "line-width": 3,
            "line-blur": 10
        },
        "filter": ["==", "id", ""]
    });
}

function addStatLayers(key) {
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
    //for (var i = colors.length-1; i < )
    var breakpoints = colors.reverse().map(function(e, i) {
        return [ (colors.length-5-i)*0.0002, e ];
    });

    //var breakpoints = [
    //    [0.005, '#0000FF'],
    //    [0.0001, '#00FF00'],
    //    [-10, '#FF0000']
    //];

    breakpoints.forEach(function (layer, i) {
        var filter = i == 0 ?
            [">=", "m2", layer[0]] :
            ["all",
                [">=", "m2", layer[0]],
                ["<", "m2", breakpoints[i - 1][0]]];
        if (i==breakpoints.length-1) filter = ["<", "m2", layer[0]]

        map.addLayer({
            "id": "statistics-" + i,
            "type": "fill",
            'interactive': true,
            "source": "datasets",
            "source-layer": "statistics",
            "paint": {
                "fill-color": layer[1],
                "fill-opacity": 0.4
            },
            "filter": filter
        });
    });
}

var map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/light-v8',
    center: [10, 50],
    zoom: 3,
    bearing: 0,
    pitch: 0
    //maxZoom: 8.9
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
        radius: 1,
        includeGeometry: true,
        layer: 'statistics'
    }, function (err, features) {

        if (err || !features.length) {
            return;
        }
debugger;
        //normalize
        var data = features[0].properties;
        showStats(data);
    });
});

/**
 *  Create the hover over effects on mouse moving.
 */
map.on('mousemove', function (e) {
    map.featuresAt(e.point, {
        radius: 0,
        layer: 'statistics'
    }, function (err, features) {
        map.getCanvas().style.cursor = (!err && features.length) ? 'pointer' : '';
        if (!err && features.length) {
            map.setFilter("coverage-hover", ["==", "id", features[0].properties.id]);
            map.setFilter("coverage-hover-fill", ["==", "id", features[0].properties.id]);
        } else {
            map.setFilter("coverage-hover", ["==", "id", ""]);
            map.setFilter("coverage-hover-fill", ["==", "id", ""]);
        }

    });
});






function showStats(data) {
    var labels = [];
    var points = [];
    var normalized = [];
    var line = [];
    for (var i = 1900; i < 2020; i++) {
        if (data.hasOwnProperty('' + i)) {
            var s = parseInt(data[i]);
            var g = parseInt(data[i + '_group']);
            var val = s/g;
            normalized.push([i, val]);
            points.push(val);
            labels.push(i);

            line.push( parseFloat(data.m)*i + parseFloat(data.c) );
        }
    }
    var result = regression('linear',normalized);



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
                return index % 5 === 0 ? value : '';
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