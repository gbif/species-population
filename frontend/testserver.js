var express = require('express'),
    fs = require('fs');

var app = express();

app.use(function(req, res, next) {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    res.header('Content-Type', 'application/x-protobuf');
    next();
});

app.get('/api/:z/:x/:y.pbf', function (req, res) {
    var buffer = fs.readFileSync('tim.pbf');
    res.send(buffer);
});

var server = app.listen(3002, function () {
    var host = server.address().address;
    var port = server.address().port;

    console.log('Example app listening at http://%s:%s', host, port);
});
