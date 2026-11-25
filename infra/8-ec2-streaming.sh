#!/usr/bin/env bash
# Parcial 3: arranca streaming-service en una EC2 (Amazon Linux 2023) con el rol LabRole.
# Uso (dentro de la EC2, en la carpeta del repo):
#   DB_HOST=<endpoint-rds> DB_PASSWORD=<clave> INSTANCIA_ID=streaming-ec2-1 bash infra/8-ec2-streaming.sh
# Para una segunda instancia: lanzar otra EC2 y ejecutar lo mismo con INSTANCIA_ID=streaming-ec2-2.
# Las credenciales de AWS NO se pasan: el SDK las toma del Instance Profile (LabRole).
set -euo pipefail
: "${DB_HOST:?Falta DB_HOST (endpoint de RDS)}"
: "${DB_PASSWORD:?Falta DB_PASSWORD}"
INSTANCIA_ID="${INSTANCIA_ID:-$(hostname)}"
docker build -t streaming-service ./backend/streaming-service
docker rm -f streaming-service 2>/dev/null || true
docker run -d --name streaming-service --restart unless-stopped -p 8085:8085 \
  -e DB_HOST="$DB_HOST" -e DB_NAME="${DB_NAME:-streaming_db}" \
  -e DB_USERNAME="${DB_USERNAME:-postgres}" -e DB_PASSWORD="$DB_PASSWORD" \
  -e AWS_REGION="${AWS_REGION:-us-east-1}" -e INSTANCIA_ID="$INSTANCIA_ID" \
  streaming-service
echo "streaming-service ($INSTANCIA_ID) iniciado. Logs: docker logs -f streaming-service"
