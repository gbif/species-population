'use strict';

var gulp = require('gulp'),
    del = require('del'),
    config = rootRequire('gulp/config');

gulp.task('clean-all', function () {
    return del(config.clean);
});