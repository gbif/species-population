map.boxZoom.disable();

map.on('load', function() {

    var canvas = map.getCanvasContainer();

    // Variable to hold the starting xy coordinates
    // when `mousedown` occured.
    var start;

    // Variable to hold the current xy coordinates
    // when `mousemove` or `mouseup` occurs.
    var current;

    // Variable for the draw box element.
    var box;


    // Set `true` to dispatch the event before other functions
    // call it. This is necessary for disabling the default map
    // dragging behaviour.
    canvas.addEventListener('mousedown', mouseDown, true);

    // Return the xy coordinates of the mouse position
    function mousePos(e) {
        var rect = canvas.getBoundingClientRect();
        var mapBoxPoint =  new mapboxgl.Point(
            e.clientX - rect.left - canvas.clientLeft,
            e.clientY - rect.top - canvas.clientTop
        );
        return mapBoxPoint;
    }

    function mouseDown(e) {
        // Continue the rest of the function if the shiftkey is pressed.
        if (!(e.shiftKey && e.button === 0)) return;

        // Disable default drag zooming when the shift key is held down.
        map.dragPan.disable();

        // Call functions for the following events
        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
        document.addEventListener('keydown', onKeyDown);

        // Capture the first xy coordinates
        start = mousePos(e);
    }

    function onMouseMove(e) {
        // Capture the ongoing xy coordinates
        current = mousePos(e);
        console.log(JSON.stringify(e.lngLat));

        // Append the box element if it doesnt exist
        if (!box) {
            box = document.createElement('div');
            box.classList.add('boxdraw');
            canvas.appendChild(box);
        }

        var minX = Math.min(start.x, current.x),
            maxX = Math.max(start.x, current.x),
            minY = Math.min(start.y, current.y),
            maxY = Math.max(start.y, current.y);

        // Adjust width and xy position of the box element ongoing
        var pos = 'translate(' + minX + 'px,' + minY + 'px)';
        box.style.transform = pos;
        box.style.WebkitTransform = pos;
        box.style.width = maxX - minX + 'px';
        box.style.height = maxY - minY + 'px';
    }

    function onMouseUp(e) {
        // Capture xy coordinates
        finish([start, mousePos(e)]);
    }

    function onKeyDown(e) {
        // If the ESC key is pressed
        if (e.keyCode === 27) finish();
    }

    function finish(bbox) {
        // Remove these events now that finish has been called.
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('keydown', onKeyDown);
        document.removeEventListener('mouseup', onMouseUp);

        if (box) {
            box.parentNode.removeChild(box);
            box = null;
        }

        // If bbox exists. use this value as the argument for `queryRenderedFeatures`
        if (bbox) {
            customQuery(bbox);
        }

        map.dragPan.enable();
    }

    map.on('mousemove', function(e) {
        //var features = map.queryRenderedFeatures(e.point, { layers: ['counties-highlighted'] });
        //// Change the cursor style as a UI indicator.
        //map.getCanvas().style.cursor = (features.length) ? 'pointer' : '';
        //
        //if (!features.length) {
        //    popup.remove();
        //    return;
        //}
        //
        //var feature = features[0];
        //
        //popup.setLngLat(e.lngLat)
        //    .setText(feature.properties.COUNTY)
        //    .addTo(map);
    });
});


function customQuery(bbox) {
    var start = map.unproject(bbox[1]);
    var end = map.unproject(bbox[0]);
    var minLat = Math.min(start.lat, end.lat);
    var minLng = Math.min(start.lng, end.lng);
    var maxLat = Math.max(start.lat, end.lat);
    var maxLng = Math.max(start.lng, end.lng);

    var path = 'http://tiletest.gbif.org/'+key+'/regression.json?minLat='+minLat+'&maxLat='+maxLat+'&minLng='+minLng+'&maxLng='+maxLng+'&minYear='+minYear+'&maxYear='+maxYear+'&yearThreshold=' + yearThreshold;

    $.ajax(path).done(function(data) {
        console.log(data);
        showStats(data);
    })
    .fail(function() {
        alert( "error in communication with API" );
    });

}