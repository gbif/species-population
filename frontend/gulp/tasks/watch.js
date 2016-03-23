var gulp = require('gulp'),
    config = rootRequire('gulp/config');

gulp.task('watch', ['browserSync'], function() {
    gulp.watch([config.style.paths], ['stylus-reload']);
    gulp.watch([config.js.paths], ['scripts-reload']);
    gulp.watch([config.html.paths], ['html-reload']);
});
