#!/usr/bin/env bash
#
# Copyright 2026 the parqet4j authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Build the client + dependency classpath (once) and run an example via the JDK
# multi-file source launcher.
#
#   examples/run.sh Authorize 01234567-89ab-7000-8000-000000000000
#   PARQET_ACCESS_TOKEN=... examples/run.sh ListPortfolios
#   PARQET_ACCESS_TOKEN=... examples/run.sh Performance <portfolio-id> --interval 1y
#
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ $# -lt 1 ]]; then
  echo "usage: examples/run.sh <Authorize|ListPortfolios|Activities|Performance|SyncActivities> [args...]" >&2
  exit 2
fi

if [[ ! -d target/classes || ! -f target/cp.txt ]]; then
  echo "building client + classpath…" >&2
  mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
fi

CP="target/classes:$(cat target/cp.txt)"
example="$1"
shift
exec java -cp "$CP" "examples/${example}.java" "$@"
