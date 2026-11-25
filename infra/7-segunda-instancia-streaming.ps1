<#
  Parcial 3: levanta una SEGUNDA instancia de streaming-service (contenedor streaming-service-2, puerto 8086)
  con la misma imagen y la misma red que la primera. Las dos ejecutan las mismas 4 aplicaciones consumidoras:
  la KCL reparte los shards de cada stream entre ambas (se ve en el panel Streaming y en la tabla de DynamoDB).

  Demo:
    1. Ejecuta este script y espera ~30-60 s: parte de los shards pasa a "streaming-2".
    2. Presiona Ctrl+C: la instancia hace checkpoint y libera sus leases; "streaming-1" los toma y sigue
       desde el último checkpoint (sin perder eventos).
  Requiere que "docker compose up" ya esté corriendo. Usa las credenciales de infra\credenciales.ps1.
#>
. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
$imagen = docker inspect streaming-service --format "{{.Config.Image}}"
$red = docker inspect streaming-service --format "{{range `$k, `$v := .NetworkSettings.Networks}}{{`$k}}{{end}}"
if (-not $imagen) { throw "No encontré el contenedor streaming-service. Ejecuta primero: docker compose up -d --build" }
Write-Host "Imagen: $imagen   Red: $red" -ForegroundColor Cyan
Write-Host "Ctrl+C detiene esta instancia: libera sus shards y la otra instancia los retoma desde el checkpoint." -ForegroundColor Yellow
docker run --rm --name streaming-service-2 --network $red -p 8086:8085 `
    -e DB_HOST=streaming-db -e DB_PORT=5432 -e DB_NAME=streaming_db -e DB_USERNAME=postgres -e DB_PASSWORD=postgres `
    -e INSTANCIA_ID=streaming-2 `
    -e AWS_REGION=$env:AWS_REGION -e AWS_ACCESS_KEY_ID=$env:AWS_ACCESS_KEY_ID `
    -e AWS_SECRET_ACCESS_KEY=$env:AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN=$env:AWS_SESSION_TOKEN `
    $imagen
