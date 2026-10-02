#!/usr/bin/env bash
# Builds everything and starts the whole demo in Docker. Needs Docker, Java 21 and Node 22.22+ (or 24).
# Usage: ./start-demo.sh [--slack] [--simulator]
#   --slack      also start the Slack MCP server
#   --simulator  work the cases in the built-in ServiceNow simulator instead of the instance in .env
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { cp .env.example .env; echo "Created .env from .env.example: add your Anthropic API key there."; }

mvn -q -B package -DskipTests
(cd shop-ui && npm ci --no-audit --no-fund && npm run build)

profiles=()
for option in "$@"; do
  case "$option" in
    --slack) profiles+=(--profile slack) ;;
    --simulator)
      profiles+=(--profile simulator)
      # These win over .env: the simulator knows only this login and the default group names.
      export AGENTIC_COMMERCE_SERVICENOW_INSTANCE_URL=http://servicenow-simulator:8088
      export AGENTIC_COMMERCE_SERVICENOW_USERNAME=trailhead.agent
      export AGENTIC_COMMERCE_SERVICENOW_PASSWORD=simulator
      export AGENTIC_COMMERCE_SERVICENOW_AGENT_GROUP="Online Shop Agent"
      export AGENTIC_COMMERCE_SERVICENOW_CUSTOMER_CARE_GROUP="Customer Care"
      export AGENTIC_COMMERCE_SERVICENOW_PAYMENTS_GROUP=Payments
      export AGENTIC_COMMERCE_SERVICENOW_FULFILMENT_GROUP=Fulfilment
      ;;
    *) echo "Unknown option $option. Usage: ./start-demo.sh [--slack] [--simulator]" >&2; exit 1 ;;
  esac
done
docker compose "${profiles[@]}" up -d --build

echo
echo "Shop and operations console: http://localhost:8080"
echo "Traces (Jaeger):             http://localhost:16686"
