(function(){


var storyEl = document.getElementById('story');
var afterStoryEl = $('#story .chapter:last')[0];

$(window).scroll(function (event) {
    var pos = storyEl.getBoundingClientRect().top;

    if (pos < 0) {
        $('#mapwrapper').addClass('sticky');
    } else {
        $('#mapwrapper').removeClass('sticky');
    }

    //var nextPos = afterStoryEl.getBoundingClientRect().top;
    //if (nextPos < 0) {
    //    $('#mapwrapper').removeClass('sticky');
    //    $('#mapwrapper').addClass('bottom');
    //} else {
    //    $('#mapwrapper').removeClass('bottom');
    //}
});


})();

var currentOccurrenceLayer;
function changeOccurrenceLayer(tiles, key) {
    if (currentOccurrenceLayer == tiles + key && map.getLayer('occurrence-tiles')) return;
    if (map && map.getLayer('occurrence-tiles')) {
        map.removeLayer('occurrence-tiles');
        map.removeSource('occurrence');
    }
    currentOccurrenceLayer = tiles + key;
    if (!tiles || !key) return;

    map.addSource('occurrence', {
        type: 'raster',
        "tiles": [tiles + key],
        "tileSize": 256
    });

    //occurrence layer
    map.addLayer({
        "id": "occurrence-tiles",
        "type": "raster",
        "source": "occurrence"
    }, 'regression');
}

var currentGeojson;
function removeGeojson() {
    if (map.getLayer('geojsonLayer')) {
        map.removeLayer('geojsonLayer');
    }
    if (map.getSource('geojson')) {
        map.removeSource('geojson');
    }
}
function addGeoJson(geojson) {
    if (JSON.stringify(currentGeojson) == JSON.stringify(geojson)) return;
    removeGeojson();

    map.addSource('geojson', {
        "type": "geojson",
        "data": geojson
    });

    map.addLayer({
        "id": "geojsonLayer",
        "type": "line",
        "source": "geojson",
        "paint": {
            "line-color": "#0000FF",
            "line-width": {
                "base": 1.5,
                "stops": [
                    [
                        5,
                        0.75
                    ],
                    [
                        18,
                        32
                    ]
                ]
            }
        }
    });
    // currentGeojson = geojson;
}

// On every scroll event, check which element is on screen
$(window).scroll(function() {
    var chapterNames = Object.keys(chapters);
    for (var i = 0; i < chapterNames.length; i++) {
        var chapterName = chapterNames[i];
        if (isElementOnScreen(chapterName)) {
            setActiveChapter(chapterName);
            break;
        }
    }
});

var activeChapterName = 'unknown';
function setActiveChapter(chapterName) {

    if (chapterName === activeChapterName) return;
    console.log('change chapter');
    $('#' + chapterName).addClass('active');
    $('#' + activeChapterName).removeClass('active');

    var chapter = chapters[chapterName];

    //changeOccurrenceLayer(chapter.occurrences, chapter.key);
    if (!chapter.key || !chapter.grid) {
        removeStatLayers();
    }
    else if (chapter.grid && (!chapters[activeChapterName] || !chapters[activeChapterName].grid || chapters[activeChapterName].key !== chapter.key)) {
        console.log('set tiles');
        //setStatLayers(chapter.key);
    }

    //geojson
    if (chapter.geojson) {
        addGeoJson(chapter.geojson);
    } else {
        removeGeojson();
    }

    //zoomable
    if (chapter.scrollZoom === false) {
        map.scrollZoom.disable();
    } else {
        map.scrollZoom.enable();
    }

    map.flyTo(chapter.location);

    activeChapterName = chapterName;
}
map.on('style.load', function () {
    setActiveChapter(activeChapterName);
});


function isElementOnScreen(id) {
    var element = document.getElementById(id);
    var bounds = element.getBoundingClientRect();
    return (bounds.top > 0 || Math.abs(bounds.top)<200 )&& bounds.top < window.innerHeight/2;
}




