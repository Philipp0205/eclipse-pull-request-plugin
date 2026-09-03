#!/usr/bin/env bash
#*******************************************************************************
# Copyright (C) 2026, Philipp Hoenisch and others
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#*******************************************************************************
#
# Finish a Tycho eclipse-repository so it is a complete p2 update site:
# uncompressed XML (GitHub Pages friendly), p2.index covering every
# metadata format that is present, plus site.xml and a browser landing page.

set -euo pipefail

if [ "$#" -ne 1 ]; then
	echo "usage: $0 <repository-directory>" >&2
	exit 1
fi

REPO=$1
if [ ! -d "$REPO" ]; then
	echo "not a directory: $REPO" >&2
	exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

unzip_jar() {
	jar=$1
	xml=$2
	if [ -f "$REPO/$jar" ] && [ ! -f "$REPO/$xml" ]; then
		unzip -o -d "$REPO" "$REPO/$jar"
	fi
}

unzip_jar content.jar content.xml
unzip_jar artifacts.jar artifacts.xml

append_if_present() {
	var_name=$1
	file=$2
	if [ -f "$REPO/$file" ]; then
		eval "$var_name=\${$var_name}${file},"
	fi
}

meta_order=
art_order=
append_if_present meta_order content.xml.xz
append_if_present meta_order content.xml
append_if_present meta_order content.jar
append_if_present art_order artifacts.xml.xz
append_if_present art_order artifacts.xml
append_if_present art_order artifacts.jar

cat > "$REPO/p2.index" <<EOF
version=1
metadata.repository.factory.order=${meta_order}!
artifact.repository.factory.order=${art_order}!
EOF

if [ -f "$SCRIPT_DIR/site.xml" ]; then
	cp "$SCRIPT_DIR/site.xml" "$REPO/site.xml"
fi

ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
if [ -f "$ROOT_DIR/site/p2-index.html" ]; then
	cp "$ROOT_DIR/site/p2-index.html" "$REPO/index.html"
fi
