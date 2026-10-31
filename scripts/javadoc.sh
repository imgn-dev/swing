#!/usr/bin/env bash
#
# Builds the API documentation of every published module of a swing checkout
# as one site, the way the JDK documentation is built: a single javadoc run
# over all modules, so that the site has one module index, one search over
# every module, and links from one module to another and to the JDK.
#
# The published modules are those with a src/main/java/module-info.java whose
# POM doesn't skip javadoc: swing-examples is read as source, not as API.
#
# Usage:  scripts/javadoc.sh <checkout> <output directory>
#         scripts/javadoc.sh . target/apidocs

set -euo pipefail

# The JDK whose API the documentation links to.
JDK=25
# The element-list of its documentation, committed beside this script, so that
# building the documentation does not depend on docs.oracle.com being reachable:
# with -Werror, javadoc fails when it cannot fetch it. Copied from the JDK's
# docs/api/element-list; the links still go to docs.oracle.com.
JDK_ELEMENT_LIST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/jdk-$JDK

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

[[ $# -eq 2 ]] || die "Usage: $0 <checkout> <output directory>"
checkout=$(cd "$1" && pwd)
output=$2
[[ -x $checkout/mvnw ]] || die "$checkout is not a swing checkout."
[[ -f $JDK_ELEMENT_LIST_DIR/element-list ]] || die "No $JDK_ELEMENT_LIST_DIR/element-list."

modules=()
source_paths=()
projects=()
for descriptor in "$checkout"/*/src/main/java/module-info.java; do
    [[ -e $descriptor ]] || continue
    project=${descriptor#"$checkout"/}
    project=${project%%/*}
    if grep -q '<maven.javadoc.skip>true</maven.javadoc.skip>' "$checkout/$project/pom.xml"; then
        continue
    fi
    # The module name follows the keyword, after an optional "open".
    name=$(grep -m 1 -oE '^(open[[:space:]]+)?module[[:space:]]+[A-Za-z0-9_.]+' "$descriptor" | awk '{print $NF}')
    [[ -n $name ]] || die "No module declaration in $descriptor."
    modules+=("$name")
    source_paths+=(--module-source-path "$name=${descriptor%/module-info.java}")
    projects+=("$project")
done
[[ ${#modules[@]} -gt 0 ]] || die "No module in $checkout."

# The third-party modules the swing modules require, from each module's compile
# class path. The swing modules themselves come from their sources: compiling
# lets the reactor resolve them without their version being in a repository.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
(cd "$checkout" && ./mvnw -B -q --no-transfer-progress -DskipTests \
    -pl "$(IFS=,; echo "${projects[*]}")" -am compile \
    dependency:build-classpath -DincludeScope=compile \
    -Dmdep.outputFile="$work/classpath-\${project.artifactId}.txt")
# The files end without a newline: awk adds it, so that the last entry of one
# file and the first of the next stay apart.
module_path=$(awk 1 "$work"/classpath-*.txt | tr ':' '\n' \
    | grep -v -e '^$' -e '/be/imgn/swing/' -e '/target/classes' | sort -u | paste -sd: -)
version=$(cd "$checkout" && ./mvnw -B -q --no-transfer-progress -N help:evaluate \
    -Dexpression=project.version -DforceStdout)
[[ -n $version ]] || die "No version in $checkout/pom.xml."

rm -rf "$output"
# The same checks as the javadoc jars of the parent POM: -Werror for
# failOnWarnings, -Xdoclint:all,-missing for doclint.
javadoc -d "$output" -quiet -notimestamp -Werror -Xdoclint:all,-missing \
    --release "$JDK" \
    "${source_paths[@]}" \
    --module "$(IFS=,; echo "${modules[*]}")" \
    ${module_path:+--module-path "$module_path"} \
    -linkoffline "https://docs.oracle.com/en/java/javase/$JDK/docs/api/" "$JDK_ELEMENT_LIST_DIR" \
    -doctitle "swing $version API" \
    -windowtitle "swing $version API"
