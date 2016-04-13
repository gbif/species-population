var gulp = require('gulp'),
    g = require('gulp-load-plugins')(),
    config = rootRequire('gulp/config');

gulp.task('optimize', function () {
    var cssStream = gulp.src(['./dist/css/**/*.css', './dist/css/**/index.css'])
        .pipe(g.concat('index.css'))
        .pipe(g.cssnano())
        .pipe(g.rename(function (path) {
            path.basename += ".min";
        }))
        .pipe(g.hash())
        .pipe(gulp.dest('./dist/css/'));

    var jsStream = gulp.src(config.js.src)
        .pipe(g.sourcemaps.init({
            loadMaps: true
        }))
        .pipe(g.concat('script.js'))
        .pipe(g.uglify())
        .pipe(g.sourcemaps.write('./'))
        .pipe(g.rename(function (path) {
            path.basename += ".min";
        }))
        .pipe(g.hash())
        .pipe(gulp.dest('./dist/scripts/'));

    return gulp.src('./dist/**/*.html')
        .pipe(g.inject(cssStream, {ignorePath: '/dist', addRootSlash: false, name: 'style'}))
        .pipe(g.inject(jsStream, {ignorePath: '/dist', addRootSlash: false}))
        .pipe(gulp.dest(config.html.dest));
});