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

    var nextPos = afterStoryEl.getBoundingClientRect().top;
    if (nextPos < 0) {
        $('#mapwrapper').removeClass('sticky');
        $('#mapwrapper').addClass('bottom');
    } else {
        $('#mapwrapper').removeClass('bottom');
    }
});


})();


function changeOccurrenceLayer(tiles) {
    if (map && map.getLayer('occurrence-tiles')) {
        map.removeLayer('occurrence-tiles');
        map.removeSource('occurrence');
    }
    map.addSource('occurrence', {
        type: 'raster',
        "tiles": [tiles],
        "tileSize": 256
    });

    //occurrence layer
    map.addLayer({
        "id": "occurrence-tiles",
        "type": "raster",
        "source": "occurrence"
    }, 'statistics');
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

var activeChapterName = 'baker';
function setActiveChapter(chapterName) {
    if (chapterName === activeChapterName) return;

    changeOccurrenceLayer(chapters[chapterName].occurrences);
    map.flyTo(chapters[chapterName].location);

    $('#' + chapterName).addClass('active');
    $('#' + activeChapterName).removeClass('active');

    activeChapterName = chapterName;
}

function isElementOnScreen(id) {
    var element = document.getElementById(id);
    var bounds = element.getBoundingClientRect();
    return (bounds.top > 0 || Math.abs(bounds.top)<200 )&& bounds.top < window.innerHeight/2;
}

