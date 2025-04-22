#!/bin/bash

set -e

echo "::group::📦 Downloading test"
curl -o ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz https://github.com/loiclefevre/test/releases/download/${VERSION}/test-linux-x86_64.tar.gz
echo "::endgroup::"

echo "::group::📦 Unpacking test"
tar -xf ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz
echo "::endgroup::"
