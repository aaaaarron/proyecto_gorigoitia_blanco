import { useEffect, useMemo, useState } from "react";
import Navbar from "../components/Navbar";
import { apiFetch, getUsuarioActual } from "../services/apiFetch";

const dinero = (valor) => new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(Number(valor) || 0);

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([]);
  const [productos, setProductos] = useState([]);
  const [productoId, setProductoId] = useState("");
  const [cantidad, setCantidad] = useState(1);
  const [carrito, setCarrito] = useState([]);
  const [tipoEntrega, setTipoEntrega] = useState("");
  const [direccion, setDireccion] = useState("");
  const [esAdmin, setEsAdmin] = useState(false);
  const [error, setError] = useState("");
  const [cargando, setCargando] = useState(true);
  const [enviando, setEnviando] = useState(false);

  async function cargar() {
    setCargando(true); setError("");
    try {
      const [listaPedidos, listaProductos] = await Promise.all([apiFetch("/api/pedidos"), apiFetch("/api/productos")]);
      setPedidos(listaPedidos); setProductos(listaProductos);
    } catch (e) { setError(e.message); } finally { setCargando(false); }
  }

  useEffect(() => { cargar(); getUsuarioActual().then((u) => setEsAdmin(u?.grupos?.includes("ADMIN"))); }, []);
  const total = useMemo(() => carrito.reduce((sum, linea) => sum + Number(linea.producto.precio) * linea.cantidad, 0), [carrito]);
  const seleccionado = productos.find((p) => String(p.id) === productoId);

  function agregar(e) {
    e.preventDefault(); setError("");
    const unidades = Number(cantidad);
    if (!seleccionado || !Number.isInteger(unidades) || unidades < 1) return setError("Selecciona un producto y una cantidad válida.");
    const yaEnCarrito = carrito.find((linea) => linea.producto.id === seleccionado.id)?.cantidad || 0;
    if (unidades + yaEnCarrito > seleccionado.stock) return setError(`Stock disponible de ${seleccionado.nombre}: ${seleccionado.stock}.`);
    setCarrito((actual) => actual.some((linea) => linea.producto.id === seleccionado.id)
      ? actual.map((linea) => linea.producto.id === seleccionado.id ? { ...linea, cantidad: linea.cantidad + unidades } : linea)
      : [...actual, { producto: seleccionado, cantidad: unidades }]);
    setCantidad(1);
  }

  function ajustar(id, delta) {
    setCarrito((actual) => actual.map((linea) => linea.producto.id === id
      ? { ...linea, cantidad: Math.min(linea.producto.stock, linea.cantidad + delta) } : linea).filter((linea) => linea.cantidad > 0));
  }

  async function crearPedido(e) {
    e.preventDefault(); setError("");
    if (!carrito.length) return setError("Agrega al menos un producto al carrito.");
    if (!tipoEntrega) return setError("Selecciona retiro o envío.");
    if (tipoEntrega === "ENVIO" && (direccion.trim().length < 8 || !/[a-zA-ZáéíóúÁÉÍÓÚñÑ]/.test(direccion) || !/\d/.test(direccion))) return setError("Ingresa una dirección válida con calle y número.");
    setEnviando(true);
    try {
      await apiFetch("/api/pedidos", { method: "POST", body: JSON.stringify({ items: carrito.map(({ producto, cantidad: unidades }) => ({ productoId: producto.id, cantidad: unidades })), tipoEntrega, direccion: tipoEntrega === "ENVIO" ? direccion.trim() : "" }) });
      setCarrito([]); setTipoEntrega(""); setDireccion(""); await cargar();
    } catch (e) { setError(e.message); } finally { setEnviando(false); }
  }

  async function cambiarEstado(id, estado) {
    setError("");
    try { await apiFetch(`/api/pedidos/${id}/estado`, { method: "PUT", body: JSON.stringify({ estado }) }); await cargar(); }
    catch (e) { setError(e.message); }
  }

  return <div className="app-shell"><Navbar /><main className="page-wrap">
    <header className="page-heading"><div><span className="eyebrow">OPERACIONES · PEDIDOS</span><h1>Pedidos</h1><p>Arma tu pedido y elige cómo recibirlo.</p></div><span className="live-indicator">● SISTEMA ACTIVO</span></header>
    {error && <div className="notice notice-error" role="alert">{error}</div>}
    <section className="order-layout">
      <div className="panel builder-panel"><div className="panel-title"><div><span className="eyebrow">NUEVO PEDIDO</span><h2>Arma tu carrito</h2></div><span className={`cart-orbit ${carrito.length ? "cart-orbit-active" : ""}`} aria-label={`${carrito.length} productos en el carrito`}><span>🛒</span><b>{carrito.length}</b></span></div>
        <form onSubmit={agregar} className="add-product-form"><label className="field"><span>Producto</span><select value={productoId} onChange={(e) => setProductoId(e.target.value)} required><option value="">Elige del catálogo</option>{productos.map((p) => <option key={p.id} value={p.id} disabled={p.stock < 1}>{p.nombre} · {dinero(p.precio)} · stock {p.stock}</option>)}</select></label><label className="field quantity-field"><span>Cantidad</span><input type="number" min="1" max={seleccionado?.stock || undefined} step="1" value={cantidad} onChange={(e) => setCantidad(e.target.value)} required /></label><button className="button button-secondary add-button" type="submit">＋ Agregar</button></form>
        {seleccionado && <div className="stock-note">Precio unitario <strong>{dinero(seleccionado.precio)}</strong><span>·</span> Stock disponible <strong>{seleccionado.stock}</strong></div>}
        <div className="cart-list" aria-live="polite">{carrito.length ? carrito.map(({ producto, cantidad: unidades }) => <article className="cart-line" key={producto.id}><div className="product-mark">{producto.nombre.slice(0, 1).toUpperCase()}</div><div className="cart-product"><strong>{producto.nombre}</strong><small>{dinero(producto.precio)} c/u · stock {producto.stock}</small></div><div className="quantity-stepper"><button type="button" aria-label={`Quitar una unidad de ${producto.nombre}`} onClick={() => ajustar(producto.id, -1)}>−</button><span>{unidades}</span><button type="button" aria-label={`Agregar una unidad de ${producto.nombre}`} disabled={unidades >= producto.stock} onClick={() => ajustar(producto.id, 1)}>＋</button></div><strong className="line-total">{dinero(producto.precio * unidades)}</strong></article>) : <div className="cart-empty"><span>＋</span><p>Tu carrito está esperando productos.</p><small>Agrega uno o varios artículos para comenzar.</small></div>}</div>
        <form onSubmit={crearPedido} className="checkout-form"><div className="delivery-options"><span className="field-label">¿Cómo lo recibes?</span><div className="delivery-choice"><label className={`delivery-option ${tipoEntrega === "RETIRO" ? "selected" : ""}`}><input type="radio" name="entrega" value="RETIRO" checked={tipoEntrega === "RETIRO"} onChange={(e) => setTipoEntrega(e.target.value)} /><span className="delivery-icon">⌂</span><span><strong>Retiro</strong><small>Lo recoges en tienda</small></span></label><label className={`delivery-option ${tipoEntrega === "ENVIO" ? "selected" : ""}`}><input type="radio" name="entrega" value="ENVIO" checked={tipoEntrega === "ENVIO"} onChange={(e) => setTipoEntrega(e.target.value)} /><span className="delivery-icon">↗</span><span><strong>Envío</strong><small>Lo llevamos a tu puerta</small></span></label></div></div>
          {tipoEntrega === "ENVIO" && <label className="field address-field"><span>Dirección de entrega</span><input autoComplete="street-address" minLength="8" placeholder="Ej. Av. Providencia 1234, Santiago" value={direccion} onChange={(e) => setDireccion(e.target.value)} required /><small>Incluye calle, número y comuna.</small></label>}
          <div className="checkout-footer"><div><span>Total del pedido</span><strong>{dinero(total)}</strong></div><button className="button button-primary checkout-button" disabled={enviando || !carrito.length}>{enviando ? "Creando pedido…" : <>{carrito.length ? `Crear pedido · ${carrito.length} ${carrito.length === 1 ? "producto" : "productos"}` : "Agrega productos"}<span> →</span></>}</button></div>
        </form>
      </div>
      <aside className="panel summary-panel"><span className="eyebrow">RESUMEN</span><div className="summary-number">{carrito.length.toString().padStart(2, "0")}</div><h3>{carrito.length === 1 ? "artículo en tu carrito" : "artículos en tu carrito"}</h3><p>Los precios y disponibilidad se verifican con el catálogo.</p><div className="summary-divider"/><div className="summary-total"><span>Total estimado</span><strong>{dinero(total)}</strong></div><div className="lamp-glow"/></aside>
    </section>
    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">ACTIVIDAD RECIENTE</span><h2>Pedidos registrados</h2></div><span className="order-count">{pedidos.length} en total</span></div>
      {cargando ? <div className="loading-state">Cargando pedidos…</div> : pedidos.length ? <div className="orders-list">{pedidos.map((p) => <article className="order-row" key={p.id}><div className="order-id">#{String(p.id).padStart(4, "0")}</div><div className="order-details"><strong>{p.items?.length ? p.items.map((item) => `${item.cantidad} × ${item.nombre || `Producto ${item.productoId}`}`).join(", ") : `${p.cantidad} × Producto ${p.productoId}`}</strong><small>{p.usuarioEmail} · {p.tipoEntrega || "Entrega por confirmar"}{p.tipoEntrega === "ENVIO" && p.direccion ? ` · ${p.direccion}` : ""}</small></div><strong className="order-price">{dinero(p.total)}</strong><span className={`status-badge status-${String(p.estado).toLowerCase()}`}>{p.estado}</span>{esAdmin && <select className="status-select" aria-label={`Cambiar estado del pedido ${p.id}`} defaultValue="" onChange={(e) => e.target.value && cambiarEstado(p.id, e.target.value)}><option value="" disabled>Actualizar</option><option value="CONFIRMADO">Confirmar</option><option value="PREPARANDO">Preparando</option><option value="LISTO">Listo</option><option value="CANCELADO">Cancelar</option></select>}</article>)}</div> : <div className="loading-state">Todavía no hay pedidos registrados.</div>}
    </section>
  </main></div>;
}
