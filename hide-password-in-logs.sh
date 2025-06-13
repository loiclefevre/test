#!/bin/bash

set -e

echo "::group::Secure password in logs"
echo "::add-mask::$PASSWORD"
echo "::endgroup::"
