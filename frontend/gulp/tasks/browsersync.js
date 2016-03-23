var gulp        = require('gulp'),
    browserSync = require('browser-sync'),
    config      = rootRequire('gulp/config');

// Static server to sync with code changes
gulp.task('browserSync', function () {
    browserSync(config.browserSync);
});