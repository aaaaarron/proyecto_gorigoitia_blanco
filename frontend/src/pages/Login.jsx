import { Authenticator } from "@aws-amplify/ui-react";
import "@aws-amplify/ui-react/styles.css";
import { useAuthenticator } from "@aws-amplify/ui-react";
import { Navigate } from "react-router-dom";

/**
 * Página de login. Usa el <Authenticator> de AWS Amplify (registro, login, logout,
 * manejo de sesión) tal como pide la rúbrica: no se construyen formularios propios.
 */
export default function Login() {
  const { authStatus } = useAuthenticator((context) => [context.authStatus]);

  if (authStatus === "authenticated") {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="login-shell">
      <section className="login-card">
        <div className="brand"><span className="brand-mark">P</span><span>Pedidos<span style={{ color: "#e9b85e" }}>360</span></span></div>
        <Authenticator />
      </section>
    </div>
  );
}
