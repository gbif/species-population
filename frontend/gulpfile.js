/**
 *   Rather than manage one giant configuration file responsible
 *   for creating multiple tasks, each task has been broken out into
 *   its own file in gulp/tasks. Any files in that directory get
 *   automatically required below.
 *   To add a new task, simply add a new task file in the tasks directory.
 */
'use strict';

var gulp = require('gulp'),
    config = require('./gulp/config'),
    runSequence = require('run-sequence'),
    requireDir = require('require-dir');

global.rootRequire = function(name) {
    return require(__dirname + '/' + name);
};

/**
 *  Require all tasks in gulp/tasks, including sub folders
 */
requireDir('./gulp/tasks', {
    recurse: true
});

gulp.task('default', [], function(callback) {
    runSequence(
        ['clean-all'], ['stylus-reload', 'scripts-reload', 'html-reload', 'assets'], ['watch'],
        callback);
});

gulp.task('prod', [], function(callback) {
    runSequence(
        ['clean-all'], ['stylus', 'scripts', 'html', 'assets'], ['optimize'],
        callback);
});