#!/bin/sh
prompt="$1"
case "$prompt" in
  *Username*|*username*) echo "$GIT_USERNAME" ;;
  *Password*|*password*|*Token*|*token*) echo "$GIT_PASSWORD" ;;
  *) echo "$GIT_PASSWORD" ;;
esac
