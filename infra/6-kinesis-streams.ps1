<#
  Parcial 3 - Kinesis Data Streams de Pedidos360 (script repetible e idempotente).

    .\infra\6-kinesis-streams.ps1 -Accion crear       # crea/ajusta los 3 streams y la alarma
    .\infra\6-kinesis-streams.ps1 -Accion estado      # muestra shards, modo, retención y alarma
    .\infra\6-kinesis-streams.ps1 -Accion eliminar    # AL TERMINAR DE TRABAJAR (Kinesis cobra por shard-hora)

  Usa las credenciales de infra\credenciales.ps1 (igual que los scripts del Parcial 2).

  Justificación de parámetros (README, sección 15):
    pedidos360-pedidos-stream     PROVISIONED · 3 shards · 48 h
      Ciclo de vida del pedido (PEDIDO_CREADO, PEDIDO_ESTADO_ACTUALIZADO). Lo leen DOS aplicaciones
      (analítica y seguimiento). Clave de partición = pedidoId: todos los eventos de un pedido van al
      mismo shard y se leen en orden. 48 h permite reprocesar (p. ej. recalcular la analítica) si un
      consumidor estuvo caído o tenía un bug.
    pedidos360-inventario-stream  PROVISIONED · 3 shards · 24 h
      Movimientos de stock (STOCK_RESERVADO, STOCK_BAJO). Clave = productoId (orden por producto).
      El valor del dato es inmediato (alerta de stock bajo): basta la retención mínima.
    pedidos360-fallidos-stream    PROVISIONED · 1 shard · 168 h
      Mensajes fallidos de cualquier consumidor. Bajo volumen (1 shard) pero 7 días para diagnosticar
      y reprocesar; siempre retiene más que los streams de negocio.
    PROVISIONED y no ON_DEMAND: la pauta pide 3 shards fijos y así el costo es predecible.
    Cada shard: escritura 1 MB/s o 1.000 registros/s, lectura 2 MB/s compartida entre las aplicaciones.
    La replicación síncrona en 3 zonas de disponibilidad la hace AWS; no se configura.
#>
param(
    [ValidateSet("crear", "estado", "eliminar")]
    [string]$Accion = "estado",
    [string]$AlarmaSnsTopicArn = ""   # opcional: ARN de pedidos360-alertas (creado por 1-crear-monitoreo.ps1)
)

. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
Requiere-AwsCli

$Streams = @(
    @{ Nombre = "pedidos360-pedidos-stream";    Shards = 3; RetencionHoras = 48  },
    @{ Nombre = "pedidos360-inventario-stream"; Shards = 3; RetencionHoras = 24  },
    @{ Nombre = "pedidos360-fallidos-stream";   Shards = 1; RetencionHoras = 168 }
)
# KCL crea una tabla de coordinación (DynamoDB) por aplicación, con el mismo nombre.
$Aplicaciones = @("pedidos360-analitica-app", "pedidos360-seguimiento-app", "pedidos360-inventario-app", "pedidos360-fallidos-app")

$Alarma       = "pedidos360-pedidos-stream-retraso-consumo"
$AlarmaStream = "pedidos360-pedidos-stream"
$UmbralMs     = 60000   # 1 min de retraso sostenido = un consumidor detenido o lento

function Existe-Stream([string]$nombre) {
    aws kinesis describe-stream-summary --stream-name $nombre 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

switch ($Accion) {

    "crear" {
        foreach ($s in $Streams) {
            Paso "$($s.Nombre)"
            $existia = Existe-Stream $s.Nombre
            if (-not $existia) {
                aws kinesis create-stream --stream-name $s.Nombre --shard-count $s.Shards --stream-mode-details StreamMode=PROVISIONED
                aws kinesis wait stream-exists --stream-name $s.Nombre
                Ok "Creado con $($s.Shards) shards (PROVISIONED)"
            } else {
                Write-Host "  Ya existe: se ajustan retención y shards" -ForegroundColor Yellow
            }
            # 1) Retención (el stream debe estar ACTIVE)
            $actual = [int](aws kinesis describe-stream-summary --stream-name $s.Nombre --query "StreamDescriptionSummary.RetentionPeriodHours" --output text)
            if ($s.RetencionHoras -gt $actual) {
                aws kinesis increase-stream-retention-period --stream-name $s.Nombre --retention-period-hours $s.RetencionHoras
            } elseif ($s.RetencionHoras -lt $actual) {
                aws kinesis decrease-stream-retention-period --stream-name $s.Nombre --retention-period-hours $s.RetencionHoras
            }
            aws kinesis wait stream-exists --stream-name $s.Nombre
            Ok "Retención $($s.RetencionHoras) h"
            # 2) Shards (deja el stream en UPDATING unos segundos)
            if ($existia) {
                $abiertos = [int](aws kinesis describe-stream-summary --stream-name $s.Nombre --query "StreamDescriptionSummary.OpenShardCount" --output text)
                if ($abiertos -ne $s.Shards) {
                    aws kinesis update-shard-count --stream-name $s.Nombre --target-shard-count $s.Shards --scaling-type UNIFORM_SCALING | Out-Null
                    Start-Sleep -Seconds 5
                    aws kinesis wait stream-exists --stream-name $s.Nombre
                    Ok "Shards $abiertos -> $($s.Shards)"
                }
            }
            aws kinesis add-tags-to-stream --stream-name $s.Nombre --tags Proyecto=Pedidos360,Asignatura=DSY1107
        }

        Paso "Alarma de CloudWatch $Alarma"
        $parametros = @("cloudwatch", "put-metric-alarm",
            "--alarm-name", $Alarma,
            "--alarm-description", "Retraso de consumo (iterator age) de $AlarmaStream mayor a 1 minuto",
            "--namespace", "AWS/Kinesis",
            "--metric-name", "GetRecords.IteratorAgeMilliseconds",
            "--dimensions", "Name=StreamName,Value=$AlarmaStream",
            "--statistic", "Maximum", "--period", "60", "--evaluation-periods", "2",
            "--threshold", "$UmbralMs", "--comparison-operator", "GreaterThanThreshold",
            "--treat-missing-data", "notBreaching")
        if ($AlarmaSnsTopicArn) { $parametros += @("--alarm-actions", $AlarmaSnsTopicArn) }
        & aws @parametros
        Ok "Alarma creada (Maximum de GetRecords.IteratorAgeMilliseconds > $UmbralMs ms, 2 de 2 minutos)"
    }

    "estado" {
        foreach ($s in $Streams) {
            if (Existe-Stream $s.Nombre) {
                aws kinesis describe-stream-summary --stream-name $s.Nombre `
                    --query "StreamDescriptionSummary.{Stream:StreamName,Estado:StreamStatus,Modo:StreamModeDetails.StreamMode,Shards:OpenShardCount,RetencionH:RetentionPeriodHours}" `
                    --output table
            } else {
                Write-Host "  [-] $($s.Nombre) no existe" -ForegroundColor DarkGray
            }
        }
        aws cloudwatch describe-alarms --alarm-names $Alarma --query "MetricAlarms[].{Alarma:AlarmName,Estado:StateValue,Umbral:Threshold}" --output table
    }

    "eliminar" {
        if ((Read-Host "Esto borra los streams, las tablas de coordinación de KCL y la alarma. Escribe SI para continuar") -ne "SI") { return }
        foreach ($s in $Streams) {
            if (Existe-Stream $s.Nombre) {
                aws kinesis delete-stream --stream-name $s.Nombre --enforce-consumer-deletion
                Ok "Stream $($s.Nombre) eliminado"
            }
        }
        $tablas = aws dynamodb list-tables --query "TableNames" --output json | ConvertFrom-Json
        foreach ($app in $Aplicaciones) {
            # KCL 3.x crea además <app>-WorkerMetricStats y <app>-CoordinatorState
            foreach ($t in @($app, "$app-WorkerMetricStats", "$app-CoordinatorState")) {
                if ($tablas -contains $t) { aws dynamodb delete-table --table-name $t | Out-Null; Ok "Tabla $t eliminada" }
            }
        }
        aws cloudwatch delete-alarms --alarm-names $Alarma
        Ok "Alarma eliminada. Ya no se cobran shards."
    }
}
