#!/bin/bash

set -eu -o pipefail

clojure -Spom
clojure \
  -J-Dclojure.compiler.direct-linking=true \
  -J-Dclojure.compiler.elide-meta='[:file :added]' \
  -M:depstar \
  -m hf.depstar.uberjar \
  --compile \
  --main net.whitbeck.eq \
  eq.jar

native-image \
  -cp eq.jar \
  --no-server \
  --no-fallback \
  --initialize-at-build-time \
  --report-unsupported-elements-at-runtime \
  -H:Name=eq \
  net.whitbeck.eq
