mapboxgl.accessToken = 'pk.eyJ1IjoiZ2JpZiIsImEiOiJjaW0xeXU1c3gwMG04dm1tNXB3cjJ3Zm12In0.8A2pUP_lgL19w4G5L0fDNw';

var gb = {};
var datasetLayer = 'http://localhost:7001/{z}/{x}/{y}.pbf';
//var datasetLayer = 'http://localhost:3002/api/{z}/{x}/{y}.pbf';

var occurrenceLayer = "http://api.gbif.org/v1/map/density/tile?x={x}&y={y}&z={z}&palette=reds&resolution=1&type=TAXON&key=797";

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

    // interactive data for the click grid layer
    map.addLayer({
        "id": "statistics",
        "type": "line",
        'interactive': true,
        "source": "datasets",
        "source-layer": "statistics",
        "paint": {
            "line-color": "#000",
            "line-opacity": 1,
            "line-width": 2
        }
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

    // a layer that activates only on a hover over a feature (a cell)
    map.addLayer({
        "id": "coverage-hover-fill",
        "type": "fill",
        "source": "datasets",
        "source-layer": "statistics",
        "layout": {},
        "paint": {
            "fill-color": "#FCA107",
            "fill-opacity": 0.2
        },
        "filter": ["==", "id", ""]
    });
}

var map = new mapboxgl.Map({
    container: 'map',
    style: 'mapbox://styles/mapbox/light-v8',
    center: [10, 50],
    zoom: 3,
    maxZoom: 8.9
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





var prop = {
    "1990": "0.1",
    "1991": "0.2",
    "1992": "0.3",
    "1993": "0.4",
    "1994": "0.5",
    "2016": "0.6",
    "totalOccurrences": "234",
    "totalAllSpeciesInGroup": "453",
    "c": "-3425",
    "id": "3/4/2/1",
    "m": "0.001"
};

var timespan = Array.apply(null, Array(2017-1900)).map(function (_, i) {return 1900 + i;});
var dataOptions = {
    min: undefined,
    max: undefined
};
timespan.forEach(function(e) {
    if (!dataOptions.min && prop.hasOwnProperty(e)) dataOptions.min = e;
    if (prop.hasOwnProperty(e)) dataOptions.max = e;
});
var labels = Array.apply(null, Array(1+dataOptions.max-dataOptions.min)).map(function (_, i) {return dataOptions.min + i;});
console.log(labels);
console.log(dataOptions);

var chart = new Chartist.Line('.ct-chart', {
    labels: ["'50", "'60", "'70", "'80", "'90", "'00", "'10"],
    // Naming the series with the series object array notation
    series: [{
        name: 'line',
        data: [0, null, null, null, null, null, 5]
    }, {
        name: 'points',
        data: [4, null, null, null, 1, 3, 6, 4]
    }
    ]
}, {
    fullWidth: true,
    axisX: {
        labelInterpolationFnc: function(value, index) {
            return index % ((dataOptions.max-dataOptions.min)/2) === 0 ? value : '';
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
