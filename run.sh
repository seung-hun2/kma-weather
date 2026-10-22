#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAVA_HOME=$(/usr/libexec/java_home -v 21)
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"
export PATH
if [ "$#" -eq 0 ]; then
  exec mvn spring-boot:run
fi
exec mvn "$@"
