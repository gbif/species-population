var gulp = require('gulp'),
    g = require('gulp-load-plugins')(),
    browserSync = require('browser-sync'),
    config = rootRequire('gulp/config');

gulp.task('scripts-reload', function() {
    return buildScripts()
        .pipe(browserSync.stream());
});

gulp.task('scripts', function() {
    return buildScripts();
});

function buildScripts() {
    return gulp.src(config.js.src)
        .pipe(gulp.dest(config.js.dest));
}