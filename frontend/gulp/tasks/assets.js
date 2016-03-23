var gulp = require('gulp'),
    config = rootRequire('gulp/config');

gulp.task('assets', [], function() {
    return gulp.src(config.assets.src)
        .pipe(gulp.dest(config.assets.dest));
});
