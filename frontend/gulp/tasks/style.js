var gulp = require('gulp'),
    path = require('path'),
    axis = require('axis'),
    lost = require('lost'),
    g = require('gulp-load-plugins')(),
    browserSync = require('browser-sync'),
    config = rootRequire('gulp/config');

gulp.task('stylus-reload', [], function() {
    return buildStylus()
        .pipe(browserSync.stream());
});

gulp.task('stylus', [], function() {
    return buildStylus();
});

function buildStylus() {
    var processors = [
        lost()
    ];

    return gulp.src(config.style.src)
        .pipe(g.sourcemaps.init())
        .pipe(g.stylus({
            use: [axis()]
        })).on('error', config.errorHandler('Stylus'))
        .pipe(g.autoprefixer()).on('error', config.errorHandler('Autoprefixer'))
        .pipe(g.postcss(processors))
        .pipe(g.if(g.util.env.production, g.cssnano()))
        .pipe(g.sourcemaps.write('./'))
        .pipe(gulp.dest(config.style.dest));
}