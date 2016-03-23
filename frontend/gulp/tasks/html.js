var gulp = require('gulp'),
    browserSync = require('browser-sync'),
    config = rootRequire('gulp/config');

gulp.task('html-reload', [], function() {
    return buildHtml()
        .pipe(browserSync.stream());
});

gulp.task('html', [], function() {
    return buildHtml();
});

function buildHtml() {
    return gulp.src(config.html.src)
        .pipe(gulp.dest(config.html.dest));
}