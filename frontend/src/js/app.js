$('.media').on('click', function(event) {
    var speciesKey = $(this).data().key;
    if (speciesKey) {
        key = speciesKey;
        $('.media').removeClass('isActive');
        $(this).addClass('isActive');
        initMap(map);
    }
});





var species = new Bloodhound({
    datumTokenizer: Bloodhound.tokenizers.obj.whitespace('canonicalName'),
    queryTokenizer: Bloodhound.tokenizers.whitespace,
    prefetch: 'http://tiletest.gbif.org/autocomplete?prefix=a',
    remote: {
        url: 'http://tiletest.gbif.org/autocomplete?prefix=%QUERY',
        wildcard: '%QUERY'
    }
});

$('#speciesTypeahead .typeahead').typeahead(null, {
    name: 'species',
    display: function(s){
        return s.scientificName;
    },
    source: species
});

$('#speciesTypeahead .typeahead').bind('typeahead:select', function(ev, suggestion) {
    key = suggestion.speciesKey;
    $('.media').removeClass('isActive');
    $('#speciesTypeahead').addClass('isActive');
    $('#speciesTypeahead').data('key', key);
    initMap(map);
});

$('.blanket__close').on('click', function(e) {
   $('.blanket').hide();
    e.preventDefault();
});
$('.blanket__open').on('click', function(e) {
    $('.blanket').show();
    e.preventDefault();
});
$('.storyline__close').on('click', function(e) {
    $('main').addClass('mapFocus');
    map.resize();
    e.preventDefault();
});

//function updateSpecies(select) {
//    speciesKey = select.value;
//    $(select).parent().parent().data('key', speciesKey);
//}
//
//
//
//$(document).ready(function() {
//    $.ajax({
//        type: "GET",
//        url: "assets/species.txt",
//        dataType: "text",
//        success: function(csvData) {
//            var species = processCsvData(csvData);
//            species.lines.forEach(function(e) {
//                $('#speciesSelect')
//                    .append($('<option>', { value : e.key })
//                        .text(e.name));
//            });
//        }
//    });
//});
//
//function processCsvData(allText) {
//    var allTextLines = allText.split(/\r\n|\n/);
//    var headers = allTextLines[0].split(',');
//    var lines = [];
//
//    for (var i=1; i<allTextLines.length; i++) {
//        var data = allTextLines[i].split(',');
//        if (data.length == headers.length) {
//
//            var record = {};
//            for (var j=0; j<headers.length; j++) {
//                record[headers[j]] = data[j];
//            }
//            lines.push(record);
//        }
//    }
//    return {
//        header: headers,
//        lines: lines
//    };
//}