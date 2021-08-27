#!/bin/sh
set -e

PRE_PWD=$(pwd)
WORKDIR=$(dirname "$(readlink -f ${0})")
cd $WORKDIR

export IMAGE_PY_DEPS=${IMAGE_PY_DEPS:-goloop/py-deps:latest}
export IMAGE_ROCKSDB_DEPS=${IMAGE_ROCKSDB_DEPS:-goloop/rocksdb-deps:latest}

ENGINE=${1}
IMAGE_SUFFIX=-${ENGINE}
export GOBUILD_TAGS=${GOBUILD_TAGS}
if [ ! -z "${GOBUILD_TAGS}" ] && [ -z "${GOBUILD_TAGS##*rocksdb*}" ]; then
  DB_TYPE=rocksdb
  IMAGE_SUFFIX=${IMAGE_SUFFIX}-rocksdb
fi
IMAGE_BASE=${IMAGE_BASE:-goloop/base${IMAGE_SUFFIX}:latest}

./update.sh "${ENGINE}" "${IMAGE_BASE}" ../..

cd $PRE_PWD
