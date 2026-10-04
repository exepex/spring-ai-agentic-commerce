#!/usr/bin/env bash
# The scenario suite in one command: starts the whole demo, then runs agent-evals against it with the real model.
# ServiceNow is the built-in simulator; with --live it is the instance in .env (see README, "ServiceNow incidents").
# Needs what start-demo.sh needs and an Anthropic API key in .env. A run costs some model usage.
# Usage: ./run-scenarios.sh [--live]
set -euo pipefail
cd "$(dirname "$0")"

case "${1:-}" in
  --live)
    ./start-demo.sh
    # The scenarios play the service desk and the teams on the same instance, with the same login. As for docker
    # compose, a variable already set in the shell wins over .env.
    # A value in matching single or double quotes loses them, as Compose reads it.
    while IFS='=' read -r name value; do
      [[ "$name" =~ ^[A-Z_][A-Z0-9_]*$ ]] || continue
      if [[ ${#value} -ge 2 && ( "$value" == \'*\' || "$value" == \"*\" ) ]]; then
        value="${value:1:${#value}-2}"
      fi
      [ -n "${!name:-}" ] || export "$name=$value"
    done < .env
    servicenow=()
    ;;
  "")
    ./start-demo.sh --simulator
    servicenow=(-Devals.servicenow.url=http://localhost:8088 -Devals.servicenow.username=trailhead.agent
      -Devals.servicenow.password=simulator "-Devals.servicenow.agent-group=Online Shop Agent")
    ;;
  *) echo "Usage: ./run-scenarios.sh [--live]" >&2; exit 1 ;;
esac

echo "Waiting for every service to answer"
for path in catalog/api/products "orders/api/orders?customerEmail=nobody@example.com" \
    payments/api/admin/simulated-outage "shipping/api/shipments?status=SHIPPED" governance/api/cases agents/api/agents; do
  for attempt in $(seq 1 60); do
    curl -fs --max-time 10 "http://localhost:8080/svc/$path" > /dev/null && break
    [ "$attempt" = 60 ] && { echo "/svc/$path did not answer; see docker compose logs" >&2; exit 1; }
    sleep 5
  done
done

# The evals place orders the way the shop's MCP server does, with the services' token: the shell's, else .env's.
token="${AGENTIC_COMMERCE_INTERNAL_API_TOKEN:-}"
if [ -z "$token" ] && [ -f .env ]; then
  token="$(sed -n 's/^AGENTIC_COMMERCE_INTERNAL_API_TOKEN=//p' .env | tail -n 1)"
  if [[ ${#token} -ge 2 && ( "$token" == \'*\' || "$token" == \"*\" ) ]]; then
    token="${token:1:${#token}-2}"
  fi
fi

mvn -B -pl agent-evals -Pevals test "${servicenow[@]}" "-Devals.internalApiToken=${token:-dev-internal-api-token}"
