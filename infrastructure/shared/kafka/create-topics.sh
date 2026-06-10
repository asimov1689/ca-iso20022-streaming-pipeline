#!/bin/sh
set -eu

: "${CA_KAFKA_BOOTSTRAP_SERVERS:=kafka:9092}"

kafka-topics --create --if-not-exists \
  --topic ca.confirmations.raw \
  --bootstrap-server "$CA_KAFKA_BOOTSTRAP_SERVERS" \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --topic ca.confirmations.formatted \
  --bootstrap-server "$CA_KAFKA_BOOTSTRAP_SERVERS" \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --topic ca.confirmations.enriched \
  --bootstrap-server "$CA_KAFKA_BOOTSTRAP_SERVERS" \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --topic ca.dead-letter \
  --bootstrap-server "$CA_KAFKA_BOOTSTRAP_SERVERS" \
  --partitions 1 \
  --replication-factor 1
