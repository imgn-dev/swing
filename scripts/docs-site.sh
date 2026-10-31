#!/usr/bin/env bash
#
# Maintains the documentation in a checkout of the gh-pages branch, served at
# https://imgn-dev.github.io/swing/:
#
#   api/snapshot/    the API documentation of the main branch, replaced as a
#                    whole on every push to it
#   api/index.html   the list of versions
#
# The layout is gaia's, so that released versions can join the snapshot under
# api/<version>/ once swing has releases.
#
# Usage:  scripts/docs-site.sh snapshot <site> <javadoc>

set -euo pipefail

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

usage() {
    die "Usage: $0 snapshot <site> <javadoc>"
}

refresh() {
    local snapshot
    # The version, such as 0.1.0-SNAPSHOT, is in the title javadoc gives the overview.
    snapshot=$(sed -nE 's|.*<title>.*\(swing (.+) API\)</title>.*|\1|p' "$site/api/snapshot/index.html" 2>/dev/null \
        | head -n 1 || true)

    {
        cat <<'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>swing API documentation</title>
</head>
<body>
<h1>swing API documentation</h1>
<ul>
EOF
        if [[ -d $site/api/snapshot ]]; then
            printf '<li><a href="snapshot/">Snapshot%s</a></li>\n' "${snapshot:+ ($snapshot)}"
        fi
        cat <<'EOF'
</ul>
</body>
</html>
EOF
    } >"$site/api/index.html"

    # GitHub Pages serves the files as they are, without Jekyll.
    touch "$site/.nojekyll"
}

[[ $# -eq 3 ]] || usage
action=$1
site=$2
[[ -d $site ]] || die "$site is not a directory."

case $action in
snapshot)
    javadoc=$3
    [[ -f $javadoc/index.html ]] || die "$javadoc has no index.html."
    mkdir -p "$site/api"
    rm -rf "$site/api/snapshot"
    cp -R "$javadoc" "$site/api/snapshot"
    ;;
*)
    usage
    ;;
esac

refresh
