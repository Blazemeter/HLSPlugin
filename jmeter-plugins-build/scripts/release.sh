#/bin/bash
set -ex

eval $(ssh-agent -s)
/set-git-credentials.sh

# get a new clean copy of master branch
git checkout master && git reset --hard origin/master

# we need this to later on use with nextSnapshot due to https://github.com/mojohaus/versions-maven-plugin/issues/207
ORIGINAL_VERSION=$(mvn -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive exec:exec)

# get a new clean copy of production branch
git checkout production && git reset --hard origin/production
git merge master
mvn versions:set -DremoveSnapshot
/execute-on-vnc.sh mvn --batch-mode clean verify
mvn --batch-mode scm:checkin -Dmessage="[RELEASE][skip ci] Fix release version \${project.version}" -Dincludes=pom.xml
mvn --batch-mode scm:check-local-modification -DpushChanges=false
mvn --batch-mode scm:tag -Dtag="\${project.version}"

git checkout master && git merge production
mvn --batch-mode versions:set -DnextSnapshot=true
mvn --batch-mode scm:checkin -Dmessage="[RELEASE][skip ci] Increase version to next development version \${project.version}" -Dincludes=pom.xml
