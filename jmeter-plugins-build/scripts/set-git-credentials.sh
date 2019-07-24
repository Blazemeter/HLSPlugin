#/bin/bash
# this has been extracted from https://docs.gitlab.com/ee/ci/ssh_keys/#verifying-the-ssh-host-keys
set -e

echo "$SSH_PRIVATE_KEY" | tr -d '\r' | ssh-add - > /dev/null
mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "$SSH_KNOWN_HOSTS" > ~/.ssh/known_hosts
chmod 644 ~/.ssh/known_hosts
git config --global user.email "gitlab@abstracta.us"
git config --global user.name "Gitlab"
