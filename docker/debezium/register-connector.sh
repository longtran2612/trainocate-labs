#!/bin/sh
# Register the Debezium PostgreSQL connector for account-service CDC
# Run this AFTER kafka-connect is healthy:
#   docker exec mt-kafka-connect curl -sf http://localhost:8083/
# Or just: sh docker/debezium/register-connector.sh

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"

echo "Registering Debezium account connector at $CONNECT_URL ..."

curl -s -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "account-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "postgres",
      "database.password": "123456",
      "database.dbname": "account_db",
      "topic.prefix": "account_db",
      "table.include.list": "public.accounts",
      "plugin.name": "pgoutput",
      "slot.name": "debezium_account",
      "publication.name": "dbz_publication",
      "decimal.handling.mode": "string",
      "time.precision.mode": "connect",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "none"
    }
  }'

echo ""
echo "Done. Check status with:"
echo "  curl $CONNECT_URL/connectors/account-connector/status"
