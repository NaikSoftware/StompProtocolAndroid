[HTML5 Boilerplate homepage](https://html5boilerplate.com/) | [Documentation
table of contents](TOC.md)

# The JavaScript

Information about the default JavaScript included in the project.

## main.js

This file can be used to contain or reference your site/app JavaScript code. If
you're working on something more advanced you might replace this file entirely.
That's cool.

## plugins.js

This file can be used to contain all your plugins, such as jQuery plugins and
other 3rd party scripts for a simple site.

One approach is to put jQuery plugins inside of a `(function($){ ...})(jQuery);`
closure to make sure they're in the jQuery namespace safety blanket. Read more
about [jQuery plugin authoring](https://learn.jquery.com/plugins/).

By default the `plugins.js` file contains a small script to avoid `console`
errors in browsers that lack a `console`. The script will make sure that, if a
console method isn't available, that method will have the value of empty
function, thus, preventing the browser from throwing an error.

## vendor

This directory can be used to contain all 3rd party library code.

Our custom build of the Modernizr library is included by
default. You may wish to create your own [custom Modernizr build with the online
builder](https://modernizr.com/download/) or [command line
tool](https://modernizr.com/docs#command-line-config).
