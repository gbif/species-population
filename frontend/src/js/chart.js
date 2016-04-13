

function showStats(data) {

    var labels = [];
    var points = [];
    //var normalized = [];
    var line = [];
    var groupLine = [];

    var speciesCounts = typeof data.speciesCounts === 'string' ? JSON.parse(data.speciesCounts) : data.speciesCounts;
    var groupCounts = typeof data.groupCounts === 'string' ? JSON.parse(data.groupCounts) : data.groupCounts;

    for (var i = minYear; i < maxYear; i++) {
        labels.push(i);
        if (speciesCounts.hasOwnProperty(i.toString())) {
            var s = speciesCounts[i];
            var g = groupCounts[i];
            var val = s/g;
            points.push(val);
        }
        else {
            points.push(null);
        }
        line.push( data.slope*i + data.intercept );
    }



    function getMaxOfArray(numArray) {
        return Math.max.apply(null, numArray);
    }

    for (var i = minYear; i < maxYear; i++) {
        if (groupCounts.hasOwnProperty(i.toString())) {
            var g = groupCounts[i];
            groupLine.push(g);
        }
        else {
            groupLine.push(null);
        }
    }
    var maximumPercentage = getMaxOfArray(points);
    var maximumGroupCount = getMaxOfArray(groupLine);
    groupLine = groupLine.map(function(e){
        return maximumPercentage*e/maximumGroupCount
    });



    var interval = (maxYear-minYear) > 100 ? 25: 10;


    var chart = new Chartist.Line('.ct-chart', {
        labels: labels,
        // Naming the series with the series object array notation
        series: [
            {
                name: 'points',
                data: points
            },
            {
                name: 'line',
                data: line
            },
            {
                name: 'groupLine',
                data: groupLine
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
        low: 0,
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
            },
            'groupLine': {
                lineSmooth: Chartist.Interpolation.none({
                    fillHoles: true
                }),
                showArea: true,
                showLine: false,
                showPoint: false
            }
        },
        plugins: [
            Chartist.plugins.tooltip({
            })
        ]
    });

    $('.charts').show();
    $('#stats').show();



    //$('#interceptStdErr span').html(data.interceptStdErr);
    //$('#meanSquareError span').html(data.meanSquareError);
    //$('#significance span').html(data.significance);
    //$('#slopeStdErr span').html(data.slopeStdErr);

    $('.statsHighlight .statsHighlight__year>div').attr('title', data.slope);
    $('.statsHighlight .statsHighlight__year>div').html( (Math.round(1000000*data.slope)/100) + ' ‱');
    if (data.slope < 0) $('.statsHighlight .statsHighlight__year').addClass('negative');
    else $('.statsHighlight .statsHighlight__year').removeClass('negative');

    if (line[0] > 0 && line[line.length-1] > 0) {
        var estChange = Math.round(100*line[line.length-1]/line[0]);
        $('.statsHighlight .statsHighlight__yearspan>div').html(estChange + ' %');
        if (data.slope < 0) $('.statsHighlight .statsHighlight__yearspan').addClass('negative');
        else $('.statsHighlight .statsHighlight__yearspan').removeClass('negative');
        $('.statsHighlight .statsHighlight__yearspan').show();
    } else {
        $('.statsHighlight .statsHighlight__yearspan').hide();
    }
}




//draw example
//var chart = new Chartist.Line('.ct-example', {
//    labels: [1968, 2002],
//    // Naming the series with the series object array notation
//    series: [
//        {
//            name: 'points',
//            data: [0.33, 0.5]
//        },
//        {
//            name: 'line',
//            data: [0.33,0.5]
//        },
//        {
//            name: 'groupLine',
//            data: [0.5, 0.25]
//        }
//    ]
//}, {
//    fullWidth: true,
//    axisX: {
//        showGrid: false
//    },
//    axisY: {
//        offset: 50
//    },
//    low: 0,
//    chartPadding: 30,
//    series: {
//        'line': {
//            lineSmooth: Chartist.Interpolation.none({
//                fillHoles: true
//            }),
//            showLine: true,
//            showArea: false,
//            showPoint: false
//        },
//        'points': {
//            showLine: false,
//            showArea: false,
//            showPoint: true
//        },
//        'groupLine': {
//            lineSmooth: Chartist.Interpolation.none({
//                fillHoles: true
//            }),
//            showArea: true,
//            showLine: false,
//            showPoint: false
//        }
//    }
//});