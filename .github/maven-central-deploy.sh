#!/bin/bash
#
# Deploys release artifacts to Maven Central via the Sonatype Central Portal.
#
# Required environment variables: GPG_SECRET_KEYS, GPG_OWNERTRUST, GPG_EXECUTABLE

set -eo pipefail

echo $GPG_SECRET_KEYS | base64 --decode | $GPG_EXECUTABLE --batch --import
echo $GPG_OWNERTRUST | base64 --decode | $GPG_EXECUTABLE --batch --import-ownertrust
mvn --batch-mode deploy -Prelease -DskipTests --settings .github/settings.xml
