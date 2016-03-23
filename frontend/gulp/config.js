'use strict';

var path = require('path');

var dest = './dist';
var src = './src/';
var config = {
    clean: path.join(dest, '**/*.*'),
    style: {
        paths: path.join(src, 'style/**/*.styl'),
        src: [path.join(src, 'style/index.styl')],
        dest: path.join(dest, '/css')
    },
    js: {
        paths: [path.join(src, 'js/**/*.js')],
        src: ['./bower_components/chartist/dist/chartist.min.js', path.join(src, 'js/mapbox-gl_patched_issue2236.js'), path.join(src, 'js/**/*.js')],
        dest: path.join(dest, 'scripts')
    },
    html: {
        paths: path.join(src, 'html/**/*.html'),
        src: path.join(src, 'html/**/*.html'),
        dest: dest
    },
    assets: {
        src: [path.join(src, 'assets/**/*.*')],
        dest: path.join(dest, 'assets')
    },
    browserSync: {
        server: {
            baseDir: dest
        }
    }
};


/**
 *  Common implementation for an error handler of a Gulp plugin
 */
config.errorHandler = function(title) {
    return function(err) {
        gutil.log(gutil.colors.red('[' + title + ']'), err.toString());
        this.emit('end');
    };
};


module.exports = Object.freeze(config);