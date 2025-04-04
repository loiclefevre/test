#!/bin/bash
set -e
echo "::group::📦 Downloading test"
wget https://github.com/loiclefevre/test/releases/download/${VERSION}/test-linux-x86_64.tar.gz
echo "::endgroup::"

echo "::group::📦 Unpacking test"
tar -xf test-linux-x86_64.tar.gz
echo "::endgroup::"
