var chapters = {
    'copenhagen': {
        location: {
            center: [12.59, 55.67],
            zoom: 5
        },
        occurrences: "http://api.gbif.org/v1/map/density/tile?x={x}&y={y}&z={z}&palette=reds&resolution=2&type=TAXON&key=" + key
    },
    'denmark': {
        location: {
            duration: 1000,
            center: [12.59, 55.67],
            zoom: 5
        },
        occurrences: "http://api.gbif.org/v1/map/density/tile?x={x}&y={y}&z={z}&palette=reds&resolution=2&type=TAXON&key=" + key
    },
    'london': {
        location: {
            center: [-0.08533793, 51.50438536],
            zoom: 2,
            speed: 0.6
        }
    },
    'woolwich': {
        location: {
            center: [0.05991101, 51.48752939],
            zoom: 4
        }
    }
};


var sampleAreaGeojson = [
    {
        "type": "Feature",
        "geometry": {
            "type": "Point",
            "coordinates": [-77.03238901390978,38.913188059745586]
        },
        "properties": {
            "title": "Mapbox DC",
            "description": "1714 14th St NW, Washington DC",
            "marker-color": "#fc4353",
            "marker-size": "large",
            "marker-symbol": "monument"
        }
    },
    {
        "type": "Feature",
        "geometry": {
            "type": "Point",
            "coordinates": [-122.414, 37.776]
        },
        "properties": {
            "title": "Mapbox SF",
            "description": "155 9th St, San Francisco",
            "marker-color": "#fc4353",
            "marker-size": "large",
            "marker-symbol": "harbor"
        }
    }
];