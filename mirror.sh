#!/usr/bin/env bash
set -o errexit ; set -o errtrace ; set -o pipefail

git config --global user.email "devops@wisetime.com"
git config --global user.name "WiseTime Bot"
git fetch --tags
git checkout --orphan temp_branch
rm -rf bamboo-specs mirror.sh
git add -A
git commit -am "Mirror Repo"
git branch -D master
git branch -m master
mkdir -p ~/.ssh
# Ensure we can talk to GitHub
ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
mkdir -p /tmp/.ssh
echo "${GITHUB_SSH_KEY_B64}" | base64 -d > /tmp/.ssh/github.key
chmod 600 /tmp/.ssh/github.key
# Push to GitHub mirror
git remote add github git@github.com:wisetime-io/wisetime-sql-connector.git
GIT_SSH_COMMAND='ssh -i /tmp/.ssh/github.key' git push -f github master --tags
rm -rf /tmp/.ssh
echo "Push to GitHub mirror complete"
