var gulp = require('gulp'),
    g = require('gulp-load-plugins')(),
    config = rootRequire('gulp/config');

gulp.task('optimize', function () {
    var cssStream = gulp.src('./dist/css/**/*.css')
        //.pipe(g.concat('index.css'))
        //.pipe(g.cssnano())
        .pipe(g.rename(function (path) {
            path.basename += ".min";
        }))
        .pipe(g.hashFilename({ "format": "{name}-{hash}{ext}" }))
        .pipe(gulp.dest('./dist/css/'));

    var jsStream = gulp.src('./dist/scripts/**/*.js')
        .pipe(g.sourcemaps.init({
            loadMaps: true
        }))
        .pipe(g.concat('script.js'))
        .pipe(g.uglify())
        .pipe(g.sourcemaps.write('./'))
        .pipe(g.rename(function (path) {
            path.basename += ".min";
        }))
        .pipe(g.hashFilename({ "format": "{name}-{hash}{ext}" }))
        .pipe(gulp.dest('./dist/scripts/'));

    return gulp.src('./dist/**/*.html')
        .pipe(g.inject(cssStream, {ignorePath: '/dist', addRootSlash: true, name: 'style'}))
        .pipe(g.inject(jsStream, {ignorePath: '/dist', addRootSlash: true}))
        .pipe(gulp.dest(config.html.dest));
});