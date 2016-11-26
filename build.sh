#!/bin/bash

POSTS_DIR="../dfrommi.github.io/_posts"
GITHUB_BASE="https://github.com/dfrommi/blog-samples/blob/master"
GITHUB_BASE_RAW="https://raw.githubusercontent.com/dfrommi/blog-samples/master"

cd $(dirname $0)

if [ ! -e "../dfrommi.github.io" ]; then
  git clone git@github.com:dfrommi/dfrommi.github.io.git ../dfrommi.github.io
fi

if [ -z "$1" ]; then
  rm "${POSTS_DIR}"/*.md

	for inFile in */README.md; do
		"$0" "$inFile"
	done
else
	inFile=$1
	TOPIC=$(dirname "$inFile")
  echo -n "${TOPIC}..."

	GITHUB_TOPIC_URL="${GITHUB_BASE}/${TOPIC}"
	GITHUB_TOPIC_URL_RAW="${GITHUB_BASE_RAW}/${TOPIC}"
	MD=$(pandoc -t markdown_github+yaml_metadata_block -M "topic_url:${GITHUB_TOPIC_URL}" -M "topic_url_raw:${GITHUB_TOPIC_URL_RAW}" --filter ./JekyllFilter.groovy -s "$inFile" | sed -e 's/^\.\.\.$/---/' )
	POSTED=$(echo "$MD" | grep "posted:" | cut -d ':' -f 2 | tr -d "' ")
	FILENAME=${POSTED}-${TOPIC}.md

	echo "$MD" > "${POSTS_DIR}/${FILENAME}"
  echo "done"
fi
