import { useCallback, useEffect, useState } from "react";
import Navbar from "../components/Navbar";
import { adminFetch, getUsuarioActual } from "../services/apiFetch";

const REFRESCO_MS = 3000;
const STREAM_PEDIDOS = "pedidos360-pedidos-stream";

const dinero = (valor) => new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(Number(valor) || 0);
const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString("es-CL") : "—");
const corto = (texto, n = 14) => (texto && texto.length > n ? `…${texto.slice(-n)}` : texto || "—");
const ms = (valor) => (valor == null ? "—" : valor < 1000 ? `${valor} ms` : `${(valor / 1000).toFixed(1)} s`);

const EVENTO_DEFECTUOSO = JSON.stringify({ eventId: "demo-defectuoso", tipoEvento: "PEDIDO_CREADO", version: 1, ocurridoEn: new Date().toISOString(), estado: "PENDIENTE", total: -1500 });

/**
 * Parcial 3 — Panel de streaming (solo ADMIN). Todo pasa por mensajeria-admin (StreamAdminService):
 * streams y su configuración, aplicaciones consumidoras (reparto de shards, checkpoint y retraso), alarma,
 * datos que producen las aplicaciones en tiempo real y eventos que terminaron en el stream de fallidos.
 */
export default function Streaming() {
  const [esAdmin, setEsAdmin] = useState(null);
  const [streams, setStreams] = useState([]);
  const [aplicaciones, setAplicaciones] = useState([]);
  const [alarma, setAlarma] = useState(null);
  const [metricas, setMetricas] = useState([]);
  const [seguimiento, setSeguimiento] = useState([]);
  const [alertas, setAlertas] = useState([]);
  const [fallidos, setFallidos] = useState([]);
  const [instancias, setInstancias] = useState([]);
  const [cambios, setCambios] = useState({});
  const [error, setError] = useState("");
  const [aviso, setAviso] = useState("");
  const [ocupado, setOcupado] = useState("");

  const cargar = useCallback(async () => {
    const pedir = (path) => adminFetch(path).catch((e) => ({ __error: e.message }));
    const [s, a, al, m, seg, alr, f, inst] = await Promise.all([
      pedir("/api/admin/streams"),
      pedir("/api/admin/streaming/aplicaciones"),
      pedir("/api/admin/streaming/alarma"),
      pedir("/api/admin/streaming/metricas"),
      pedir("/api/admin/streaming/seguimiento"),
      pedir("/api/admin/streaming/alertas"),
      pedir("/api/admin/streaming/fallidos"),
      pedir("/api/admin/streaming/instancias"),
    ]);
    const errores = [s, a, al, m, seg, alr, f, inst].filter((r) => r && r.__error).map((r) => r.__error);
    if (Array.isArray(s)) setStreams(s.filter((x) => x.nombre.startsWith("pedidos360")));
    if (Array.isArray(a)) setAplicaciones(a);
    if (al && !al.__error) setAlarma(al);
    if (Array.isArray(m)) setMetricas(m);
    if (Array.isArray(seg)) setSeguimiento(seg);
    if (Array.isArray(alr)) setAlertas(alr);
    if (Array.isArray(f)) setFallidos(f);
    if (Array.isArray(inst)) setInstancias(inst);
    setError(errores.length ? errores[0] : "");
  }, []);

  useEffect(() => { getUsuarioActual().then((u) => setEsAdmin(Boolean(u?.grupos?.includes("ADMIN")))); }, []);
  useEffect(() => {
    if (!esAdmin) return undefined;
    cargar();
    const t = setInterval(cargar, REFRESCO_MS);
    return () => clearInterval(t);
  }, [esAdmin, cargar]);

  async function accion(clave, path, opciones, mensajeOk) {
    setOcupado(clave); setError(""); setAviso("");
    try {
      const r = await adminFetch(path, opciones);
      setAviso(typeof mensajeOk === "function" ? mensajeOk(r) : mensajeOk);
      await cargar();
    } catch (e) { setError(e.message); } finally { setOcupado(""); }
  }

  const valorCambio = (stream, campo, actual) => cambios[`${stream}:${campo}`] ?? actual ?? "";
  const editar = (stream, campo, valor) => setCambios((c) => ({ ...c, [`${stream}:${campo}`]: valor }));

  if (esAdmin === false) {
    return <div className="app-shell"><Navbar /><main className="page-wrap"><div className="notice notice-error">403: esta sección es solo para el grupo ADMIN.</div></main></div>;
  }

  const metrica = (clave) => metricas.find((x) => x.clave === clave);
  const porEstado = metricas.filter((x) => x.clave.startsWith("ESTADO:"));
  const porMinuto = metricas.filter((x) => x.clave.startsWith("MINUTO:")).slice(-5).reverse();
  const falloSimulado = instancias.some((i) => i.falloAnaliticaSimulado);

  return <div className="app-shell"><Navbar /><main className="page-wrap">
    <header className="page-heading"><div><span className="eyebrow">OPERACIONES · STREAMING CON KINESIS</span><h1>Streaming</h1><p>2 streams de negocio + 1 de fallidos, 4 aplicaciones consumidoras. Se actualiza cada 3 s.</p></div>
      <span className="live-indicator">{alarma ? `● ALARMA ${alarma.estado}` : "● SIN DATOS DE ALARMA"}</span></header>
    {error && <div className="notice notice-error" role="alert">{error}</div>}
    {aviso && <div className="notice">{aviso}</div>}

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">DEMO</span><h2>Generar eventos</h2></div></div>
      <div className="dashboard-actions">
        <button className="button button-primary" disabled={ocupado !== ""} onClick={() => accion("rafaga", `/api/admin/streams/${STREAM_PEDIDOS}/rafaga?cantidad=30`, { method: "POST" }, (r) => `${r.enviados} eventos publicados · por shard: ${Object.entries(r.porShard).map(([k, v]) => `${corto(k, 6)}=${v}`).join(", ")}`)}>Publicar ráfaga de 30 pedidos</button>
        <button className="button button-secondary" disabled={ocupado !== ""} onClick={() => accion("defectuoso", `/api/admin/streams/${STREAM_PEDIDOS}/prueba`, { method: "POST", body: JSON.stringify({ clave: "demo", contenido: EVENTO_DEFECTUOSO }) }, (r) => `Evento defectuoso publicado (${r.detalle[0]}). Debe aparecer en "Eventos fallidos".`)}>Publicar evento defectuoso</button>
        <button className="button button-quiet" disabled={ocupado !== ""} onClick={() => accion("sim", `/api/admin/streaming/simulacion?falloAnalitica=${!falloSimulado}`, { method: "POST" }, falloSimulado ? "Fallo simulado desactivado" : "Fallo transitorio activado en analítica: los eventos se reintentan y terminan en fallidos")}>{falloSimulado ? "Desactivar fallo de analítica" : "Simular fallo de analítica"}</button>
        <button className="button button-quiet" disabled={ocupado !== ""} onClick={() => accion("aplicar", "/api/admin/streams/aplicar", { method: "POST" }, (r) => r.pasos.join(" · "))}>Aplicar configuración declarada</button>
      </div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">STREAMS</span><h2>Configuración de los streams</h2></div><span className="order-count">{streams.length} streams</span></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Stream</th><th>Rol</th><th>Estado</th><th>Modo</th><th>Shards</th><th>Retención</th><th>Cambiar shards</th><th>Cambiar retención (h)</th></tr></thead><tbody>
        {streams.map((s) => <tr key={s.nombre}>
          <td><strong>{s.nombre}</strong>{s.justificacion && <><br /><small title={s.justificacion}>{s.justificacion.slice(0, 70)}…</small></>}</td>
          <td>{s.rol || "—"}</td>
          <td><span className={`status-badge ${s.estado === "ACTIVE" ? "status-listo" : "status-cancelado"}`}>{s.estado}</span></td>
          <td>{s.modo}</td><td>{s.shardsAbiertos}</td><td>{s.retencionHoras} h</td>
          <td><input className="stream-input" type="number" min="1" max="10" value={valorCambio(s.nombre, "shards", s.shardsAbiertos)} onChange={(e) => editar(s.nombre, "shards", e.target.value)} />{" "}
            <button className="button button-quiet" disabled={ocupado !== "" || s.estado !== "ACTIVE"} onClick={() => accion(`sh-${s.nombre}`, `/api/admin/streams/${s.nombre}/shards`, { method: "PUT", body: JSON.stringify({ shards: Number(valorCambio(s.nombre, "shards", s.shardsAbiertos)) }) }, `${s.nombre}: resharding iniciado`)}>Aplicar</button></td>
          <td><input className="stream-input" type="number" min="24" max="8760" value={valorCambio(s.nombre, "ret", s.retencionHoras)} onChange={(e) => editar(s.nombre, "ret", e.target.value)} />{" "}
            <button className="button button-quiet" disabled={ocupado !== "" || s.estado !== "ACTIVE"} onClick={() => accion(`rt-${s.nombre}`, `/api/admin/streams/${s.nombre}/retencion`, { method: "PUT", body: JSON.stringify({ horas: Number(valorCambio(s.nombre, "ret", s.retencionHoras)) }) }, `${s.nombre}: retención actualizada`)}>Aplicar</button></td>
        </tr>)}
        {!streams.length && <tr><td colSpan="8">No hay streams. Usa "Aplicar configuración declarada" o infra/6-kinesis-streams.ps1.</td></tr>}
      </tbody></table></div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">APLICACIONES CONSUMIDORAS</span><h2>Reparto de shards, checkpoint y retraso</h2></div><span className="order-count">{instancias.filter((i) => !i.error).length} instancia(s) de streaming-service</span></div>
      {aplicaciones.map((a) => <div key={a.nombre} className="data-table-wrap stream-app">
        <p><strong>{a.nombre}</strong> · {a.stream} · {a.proposito}<br /><small>Tabla de coordinación (DynamoDB): {a.tablaCoordinacion} · {a.mensaje} · retraso máx.: {ms(a.retrasoMaxMs)}</small></p>
        {a.tablaExiste && <table className="data-table"><thead><tr><th>Shard</th><th>Instancia (leaseOwner)</th><th>Checkpoint (sequence number)</th><th>Renovaciones</th><th>Retraso</th><th>Procesados</th><th>Fallidos</th><th>Última lectura</th></tr></thead><tbody>
          {a.leases.map((l) => <tr key={l.shardId}><td>{l.shardId}</td><td>{l.instancia || "— (sin dueño)"}</td><td title={l.checkpoint}>{corto(l.checkpoint, 18)}</td><td>{l.leaseCounter ?? "—"}</td><td>{ms(l.millisBehindLatest)}</td><td>{l.procesados ?? "—"}</td><td>{l.fallidos ?? "—"}</td><td>{hora(l.ultimaLectura)}</td></tr>)}
        </tbody></table>}
      </div>)}
      {!aplicaciones.length && <p>Sin datos de aplicaciones consumidoras.</p>}
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">ANALÍTICA EN LÍNEA</span><h2>pedidos360-analitica-app</h2></div></div>
      <div className="stream-stats">
        <div className="dashboard-card"><span className="eyebrow">PEDIDOS</span><p className="stream-number">{metrica("PEDIDOS_CREADOS")?.cantidad ?? 0}</p></div>
        <div className="dashboard-card"><span className="eyebrow">VENTAS</span><p className="stream-number">{dinero(metrica("VENTAS_TOTALES")?.valor)}</p></div>
        <div className="dashboard-card"><span className="eyebrow">ENVÍO / RETIRO</span><p className="stream-number">{metrica("ENTREGA:ENVIO")?.cantidad ?? 0} / {metrica("ENTREGA:RETIRO")?.cantidad ?? 0}</p></div>
        <div className="dashboard-card"><span className="eyebrow">CAMBIOS DE ESTADO</span><p>{porEstado.map((x) => `${x.clave.replace("ESTADO:", "")}: ${x.cantidad}`).join(" · ") || "—"}</p></div>
        <div className="dashboard-card"><span className="eyebrow">ÚLTIMOS MINUTOS (UTC)</span><p>{porMinuto.map((x) => `${x.clave.slice(-5)} → ${x.cantidad}`).join(" · ") || "—"}</p></div>
      </div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">SEGUIMIENTO EN TIEMPO REAL</span><h2>pedidos360-seguimiento-app</h2></div></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Pedido</th><th>Estado actual</th><th>Entrega</th><th>Total</th><th>Eventos</th><th>Latencia</th><th>Shard</th><th>Actualizado</th></tr></thead><tbody>
        {seguimiento.map((p) => <tr key={p.pedidoId}><td><strong>#{p.pedidoId}</strong></td><td><span className="status-badge status-listo">{p.estadoActual}</span></td><td>{p.tipoEntrega || "—"}</td><td>{dinero(p.total)}</td><td>{p.eventos}</td><td>{ms(p.latenciaMs)}</td><td>{p.shardId}</td><td>{hora(p.actualizadoEn)}</td></tr>)}
        {!seguimiento.length && <tr><td colSpan="8">Crea un pedido o cambia su estado: aparece aquí en segundos.</td></tr>}
      </tbody></table></div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">INVENTARIO</span><h2>Alertas de stock bajo (pedidos360-inventario-app)</h2></div></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Producto</th><th>Stock restante</th><th>Detectada</th></tr></thead><tbody>
        {alertas.map((a) => <tr key={a.id}><td>{a.nombre} (#{a.productoId})</td><td>{a.stockRestante}</td><td>{hora(a.creadaEn)}</td></tr>)}
        {!alertas.length && <tr><td colSpan="3">Sin alertas.</td></tr>}
      </tbody></table></div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">STREAM DE MENSAJES FALLIDOS</span><h2>Eventos fallidos (pedidos360-fallidos-app)</h2></div><span className="order-count">{fallidos.length}</span></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Aplicación</th><th>Tipo</th><th>Intentos</th><th>Causa</th><th>Origen (shard / seq)</th><th>Detectado</th></tr></thead><tbody>
        {fallidos.map((f) => <tr key={f.id}><td>{f.aplicacion}</td><td><span className="status-badge status-cancelado">{f.tipoError}</span></td><td>{f.intentos}</td><td title={f.payloadOriginal}>{f.causa}</td><td>{f.shardId} / {corto(f.sequenceNumber)}</td><td>{hora(f.detectadoEn)}</td></tr>)}
        {!fallidos.length && <tr><td colSpan="6">Sin eventos fallidos.</td></tr>}
      </tbody></table></div>
    </section>

    {alarma && <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">CLOUDWATCH</span><h2>Alarma de retraso de consumo</h2></div></div>
      <p><strong>{alarma.nombre}</strong> · {alarma.stream} · umbral {ms(alarma.umbralMs)} · estado <strong>{alarma.estado}</strong><br /><small>{alarma.razon}</small></p>
    </section>}
  </main></div>;
}
