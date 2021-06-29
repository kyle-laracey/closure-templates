*   `requirecss`: takes a list of CSS namespaces (dotted identifiers). This is
    used to add `@requirecss` annotations in the generated JavaScript. Also, if
    there is no `cssbase` attribute, the first `requirecss` namespace can be
    used for autoprefixing in [`css` function](functions.md#css) calls.

*   `cssbase`: takes a single CSS namespace (dotted identifier). This is used
    for autoprefixing in [`css` function](functions.md#css) calls.

*   `cssprefix`: takes an explicit prefix to use for autoprefixing in
    [`css` function](functions.md#css) calls.

*   `requirecsspath` takes a list of absolute and/or relative paths for CSS
    files, without their file extensions. These can be either GSS or Sass files.
    This does NOT have any autoprefix behavior. Use of `cssbase` or `cssprefix`
    is required to autoprefix.
