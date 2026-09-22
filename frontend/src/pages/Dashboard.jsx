import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import { getUsuarioActual } from "../services/apiFetch";

export default function Dashboard() {
  const [usuario, setUsuario] = useState(null);
  useEffect(() => { getUsuarioActual().then(setUsuario); }, []);
  return <div className="app-shell"><Navbar/><main className="page-wrap"><header className="page-heading"><div><span className="eyebrow">PEDIDOS360 · TU ESPACIO</span><h1>Buenas noches</h1><p>Todo listo para gestionar tus pedidos.</p></div><span className="live-indicator">● SESIÓN SEGURA</span></header><section className="dashboard-card"><span className="eyebrow">CUENTA</span>{usuario?<><p><strong>Usuario</strong><br/>{usuario.email}</p><p><strong>Rol</strong><br/>{usuario.grupos?.length?usuario.grupos.join(", "):"Sin grupo asignado"}</p></>:<p>Cargando datos de tu cuenta…</p>}<div className="dashboard-actions"><Link className="button button-primary" to="/pedidos">Crear un pedido →</Link><Link className="button button-quiet" to="/productos">Ver catálogo</Link></div></section></main></div>;
}
