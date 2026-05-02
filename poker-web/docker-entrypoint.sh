#!/bin/sh
set -e

# Wait for backend to be reachable
echo "Waiting for backend..."
for i in $(seq 1 30); do
  if wget -qO /dev/null http://backend:8080 2>/dev/null; then
    echo "Backend is ready"
    break
  fi
  echo "  attempt $i/30..."
  sleep 2
done

exec nginx -g 'daemon off;'
