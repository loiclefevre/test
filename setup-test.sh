#!/bin/bash

set -e

echo "VERSION: ${VERSION}"
echo "GITHUB_ACTION_PATH: ${GITHUB_ACTION_PATH}"
id

echo "::group::📦 Downloading test"
wget https://github.com/loiclefevre/test/releases/download/${VERSION}/test-linux-x86_64.tar.gz -O ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz -q
echo "::endgroup::"

echo "::group::📦 Unpacking test"
tar -xf --overwrite ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz -C ${GITHUB_ACTION_PATH}/
echo "::endgroup::"
