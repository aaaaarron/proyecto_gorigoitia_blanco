import { useAuthenticator } from "@aws-amplify/ui-react";
import { Navigate } from "react-router-dom";

/**
 * Envuelve rutas que requieren sesión iniciada. Si el usuario no está autenticado,
 * lo redirige a /login (donde se muestra el <Authenticator> de Amplify).
 */
export default function ProtectedRoute({ children }) {
  const { authStatus } = useAuthenticator((context) => [context.authStatus]);

  if (authStatus === "configuring") {
    return <p style={{ padding: "2rem" }}>Cargando sesión...</p>;
  }

  if (authStatus !== "authenticated") {
    return <Navigate to="/login" replace />;
  }

  return children;
}
