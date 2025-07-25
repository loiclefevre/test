#!/bin/bash

set -e

if [ ! -f "${GITHUB_ACTION_PATH}"/setup-testpilot ]; then

echo "::group::🔽 Downloading test"
wget https://github.com/loiclefevre/test/releases/download/${VERSION}/test-linux-x86_64.tar.gz -O ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz -q
echo "::endgroup::"

echo "::group::📦 Unpacking test"
tar -xf ${GITHUB_ACTION_PATH}/test-linux-x86_64.tar.gz -C ${GITHUB_ACTION_PATH}/ --overwrite
echo "::endgroup::"

fi;
