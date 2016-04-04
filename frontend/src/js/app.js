$('.media').on('click', function(event) {
    key = $(this).data().key;
    $('.media').removeClass('isActive');
    $(this).addClass('isActive');
    initMap(map);
});
