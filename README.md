# Pedidos360

Sistema de gestión de pedidos desarrollado para la **Evaluación Parcial N°1 — DSY1107 (Desarrollo Cloud Native I, DuocUC)**.

Pedidos360 demuestra, de punta a punta, un flujo de autenticación y autorización real con **Amazon Cognito**, comunicación segura entre **microservicios Spring Boot**, y un **frontend React con AWS Amplify**, todo ejecutable localmente con Docker y listo para desplegar en AWS sin reescribir código.

---

## 1. Descripción

Pedidos360 es un sistema simple de gestión de pedidos con dos entidades de negocio (`Producto` y `Pedido`) pensado para **maximizar la demostración de los requisitos de la rúbrica** (Cognito, JWT, microservicios, roles, Docker, AWS) y minimizar la complejidad de negocio.

- Un usuario se autentica con Cognito (registro/login vía Amplify Authenticator).
- Puede ver productos y crear pedidos (rol `USER`).
- Un `ADMIN` además puede crear/editar/eliminar productos y cambiar el estado de un pedido.
- Al crear un pedido, `pedidos-service` valida y descuenta stock llamando en tiempo real a `productos-service`, reenviando el mismo JWT del usuario.

## 2. Arquitectura

```text
                         INTERNET
                             │
                             ▼
                  ┌────────────────────┐
                  │   React Frontend   │
                  │    AWS Amplify     │
                  └─────────┬──────────┘
                            │
                            ▼
                  ┌────────────────────┐
                  │  Amazon Cognito    │
                  │  Authentication    │
                  └─────────┬──────────┘
                            │ JWT
                            ▼
                  ┌────────────────────┐
                  │   API Gateway      │
                  └─────────┬──────────┘
                            │
                   ┌────────┴────────┐
                   ▼                 ▼
             ┌───────────┐     ┌───────────┐
             │    EC2    │     │    EC2    │
             │ Productos │     │ Pedidos   │
             │ Service   │────▶│ Service   │  (pedidos consulta productos)
             └─────┬─────┘     └─────┬─────┘
                   ▼                 ▼
            productos-db        pedidos-db
             (PostgreSQL)       (PostgreSQL)
```

En **local**, el "API Gateway" se simula con un reverse proxy Nginx delante del frontend (ver sección 8), y las "instancias EC2" son contenedores Docker de cada microservicio. La lógica de negocio y de seguridad es idéntica en ambos entornos — solo cambian variables de entorno.

## 3. Tecnologías y versiones

| Componente | Tecnología | Versión |
| --- | --- | --- |
| Backend | Java | 21 (LTS) |
| Backend | Spring Boot | 3.3.4 |
| Backend | Maven | 3.9 |
| Backend | Spring Security OAuth2 Resource Server | (incluido en Spring Boot 3.3.4) |
| Base de datos | PostgreSQL | 16 |
| Frontend | Node.js | 20 LTS |
| Frontend | React | 18.3 |
| Frontend | Vite | 5.4 |
| Frontend | AWS Amplify (JS v6) / `@aws-amplify/ui-react` | 6.x |
| Frontend | React Router | 6.26 |
| Contenedores | Docker / Docker Compose | Docker Desktop reciente |
| AWS | Cognito, Amplify Hosting, API Gateway, EC2 | — |

## 4. Requisitos

- Docker Desktop
- Node.js 20+ (solo si se quiere correr el frontend fuera de Docker)
- Java 21 y Maven (solo si se quiere correr un microservicio fuera de Docker)
- Git
- Una cuenta de AWS (para la parte de Cognito y el despliegue final)

## 5. Estructura del proyecto

```text
pedidos360/
├── frontend/                  # React + Vite + Amplify
├── backend/
│   ├── productos-service/     # Spring Boot — microservicio de productos
│   └── pedidos-service/       # Spring Boot — microservicio de pedidos
├── docker-compose.yml
├── .env.example
└── README.md
```

## 6. Instalación local

```bash
git clone <URL_DEL_REPOSITORIO>
cd pedidos360
cp .env.example .env
# Edita .env con los datos de tu User Pool de Cognito (ver sección 7)
docker compose up --build
```

Servicios expuestos:

| Servicio | URL local |
| --- | --- |
| Frontend | http://localhost:3000 |
| productos-service | http://localhost:8081 (Swagger: `/swagger-ui.html`) |
| pedidos-service | http://localhost:8082 (Swagger: `/swagger-ui.html`) |
| productos-db | localhost:5433 |
| pedidos-db | localhost:5434 |

## 7. Configuración de Cognito (paso a paso)

### 7.1 Crear el User Pool

1. Consola AWS → **Cognito** → **User pools** → **Create user pool**.
2. **Configure sign-in experience**: Cognito user pool sign-in options → `Email`.
3. **Configure security requirements**: password policy por defecto, MFA → `No MFA` (para simplificar el laboratorio).
4. **Configure sign-up experience**: Self-service sign-up activado, Required attributes → `email`.
5. **Configure message delivery**: Email provider → `Send email with Cognito`.
6. **Integrate your app**:
   - User pool name: `pedidos360-pool`
   - Use the Cognito Hosted UI: activado (opcional, útil para probar con Postman)
   - App type: `Public client`
   - App client name: `pedidos360-app`
   - Client secret: **Don't generate a client secret** (obligatorio: Amplify usa PKCE, no secreto)
   - Allowed callback URLs: `http://localhost:3000/` (agrega también la URL de Amplify Hosting cuando la tengas)
7. **Create user pool**.

Dónde sacar el **User Pool ID**: pestaña *Overview* del User Pool → campo *User pool ID* (formato `us-east-1_XXXXXXXXX`).

Dónde sacar el **App Client ID**: *App integration* → *App clients* → tu app client → campo *Client ID*.

### 7.2 Crear los grupos de roles

1. Tu User Pool → **Groups** → **Create group**.
2. Crea el grupo `USER` (sin rol IAM asociado, solo se usa como claim).
3. Crea el grupo `ADMIN`.
4. Asigna manualmente cada usuario de prueba a su grupo: **Users** → selecciona el usuario → **Add to group**.

Cognito incluye automáticamente los grupos del usuario en el ID Token como el claim `cognito:groups`, que es exactamente lo que leen los microservicios (`CognitoGroupsAuthoritiesConverter`) y el frontend (`getUsuarioActual`).

### 7.3 Obtener el Issuer URL (para el backend)

Tu User Pool → **Overview** → campo **OpenID Connect configuration URL**, con esta forma:

```text
https://cognito-idp.<region>.amazonaws.com/<USER_POOL_ID>/.well-known/openid-configuration
```

El `COGNITO_ISSUER_URI` que usan los microservicios es **esa misma URL sin el sufijo** `/.well-known/openid-configuration`:

```text
COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX
```

Con eso, `NimbusJwtDecoder.withIssuerLocation(...)` descubre automáticamente el JWKS y valida **firma, expiración e issuer** de cada token.

### 7.4 Configurar variables de entorno

Edita `.env` en la raíz del proyecto:

```env
COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX
CORS_ALLOWED_ORIGINS=http://localhost:3000
VITE_API_URL=
VITE_COGNITO_REGION=us-east-1
VITE_COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
VITE_COGNITO_USER_POOL_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
```

`VITE_API_URL` se deja **vacío** cuando se usa Docker Compose: el frontend queda servido por Nginx, que hace de reverse proxy hacia ambos microservicios (ver sección 8), simulando el rol de API Gateway.

## 8. API local (Nginx como API Gateway simplificado)

`frontend/nginx.conf` expone un único punto de entrada:

```text
React (puerto 3000, servido por Nginx)
  ├── /api/productos → productos-service:8081
  └── /api/pedidos   → pedidos-service:8082
```

Así, `apiFetch()` siempre llama a rutas relativas (`/api/productos`, `/api/pedidos`), y en AWS basta con apuntar `VITE_API_URL` a la Invoke URL de API Gateway — **la lógica de React no cambia**.

## 9. Roles y autorización

| Endpoint | USER | ADMIN |
| --- | --- | --- |
| `GET /api/productos` | ✅ | ✅ |
| `GET /api/productos/{id}` | ✅ | ✅ |
| `POST /api/productos` | ❌ (403) | ✅ |
| `PUT /api/productos/{id}` | ❌ (403) | ✅ |
| `DELETE /api/productos/{id}` | ❌ (403) | ✅ |
| `GET /api/pedidos` | ✅ | ✅ |
| `GET /api/pedidos/{id}` | ✅ | ✅ |
| `POST /api/pedidos` | ✅ | ✅ |
| `PUT /api/pedidos/{id}/estado` | ❌ (403) | ✅ |

La autorización se aplica en `SecurityConfig` de cada microservicio usando `hasRole(...)` sobre las authorities derivadas del claim `cognito:groups` — no es una comprobación del nombre de usuario.

## 10. Pruebas

### 10.1 Automatizadas (JUnit)

```bash
cd backend/productos-service && mvn test
cd backend/pedidos-service && mvn test
```

Cubren: creación/consulta de productos, error 404 al pedir un producto inexistente, error de stock insuficiente, creación de pedidos con `ProductosClient` simulado (Mockito), cambio de estado de un pedido.

La validación real de JWT (firma/issuer/expiración/roles) requiere un User Pool real, por lo que las pruebas 401/403 listadas abajo se verifican manualmente contra Cognito, como pide la rúbrica.

### 10.2 Manuales (contra Cognito real)

1. Registrarse desde `/login` (Authenticator de Amplify).
2. Iniciar sesión.
3. Cerrar sesión.
4. Con sesión iniciada, entrar a `/dashboard` → debe funcionar.
5. Sin sesión (o en incógnito), intentar entrar a `/dashboard` → debe redirigir a `/login`.
6. Confirmar en las DevTools que el frontend obtiene el ID Token de Cognito.
7. Confirmar que cada llamada a `/api/...` incluye `Authorization: Bearer <JWT>`.
8. Confirmar que el backend rechaza peticiones sin ese header.
9. Petición sin token (`curl` sin header) → `401`.
10. Token manipulado/inválido → `401`.
11. Token expirado (esperar a que expire o editarlo) → `401`.
12. Usuario del grupo `USER` intentando `POST /api/productos` → `403`.
13. Usuario del grupo `ADMIN` haciendo lo mismo → `200/201`.
14. Crear un producto como `ADMIN`.
15. Consultar ese producto como `USER`.
16. Crear un pedido con ese producto como `USER`.
17. Consultar el pedido creado.
18. Verificar que `pedidos-service` consultó `productos-service` (revisar logs de `productos-service`: debe registrar la petición de `reservar-stock`).

Ejemplo de prueba 9 con `curl`:

```bash
curl -i http://localhost:8081/api/productos
# Debe responder 401 Unauthorized
```

## 11. Checklist de rúbrica

| Requisito | Implementación | Archivo | Cómo demostrarlo |
| --- | --- | --- | --- |
| React | SPA con Vite | `frontend/src/App.jsx` | `npm run dev` o Docker |
| Amplify | `Amplify.configure` con Cognito | `frontend/src/amplifyConfig.js` | Login funcional |
| Authenticator | `<Authenticator>` de `@aws-amplify/ui-react` | `frontend/src/pages/Login.jsx` | Pantalla `/login` |
| Login/Logout | Flujo completo Amplify | `Login.jsx`, `Navbar.jsx` | Pruebas 1-3 |
| Rutas protegidas | `ProtectedRoute` + `useAuthenticator` | `frontend/src/router/ProtectedRoute.jsx` | Prueba 5 |
| JWT | ID Token de Cognito vía `fetchAuthSession` | `frontend/src/services/apiFetch.js` | Prueba 6 |
| apiFetch | Helper centralizado con header `Authorization` | `apiFetch.js` | Prueba 7 |
| Claims | Lectura de `email` / `cognito:groups` | `getUsuarioActual()`, `Dashboard.jsx` | Vista Dashboard |
| Roles | Grupos Cognito `USER`/`ADMIN` | Grupos Cognito + UI condicional | Sección 9 |
| Microservicio 1 | `productos-service` (Spring Boot, CRUD) | `backend/productos-service/` | `mvn test`, Swagger |
| Microservicio 2 | `pedidos-service` (Spring Boot, consume productos) | `backend/pedidos-service/` | `mvn test`, Swagger |
| Spring Security | OAuth2 Resource Server en ambos servicios | `config/SecurityConfig.java` (cada servicio) | Pruebas 9-13 |
| Firma JWT | `NimbusJwtDecoder` vía JWKS del issuer | `SecurityConfig.jwtDecoder()` | Prueba 10 |
| Issuer | `withIssuerLocation(issuerUri)` | `application.yml` → `cognito.issuer-uri` | Prueba 10 |
| Expiración | Validada automáticamente por `NimbusJwtDecoder` | — | Prueba 11 |
| 401 | Sin token / token inválido / expirado | Spring Security (Resource Server) | Pruebas 9-11 |
| 403 | Rol insuficiente | `hasRole(...)` en `SecurityConfig` | Prueba 12 |
| PostgreSQL | Una BD por microservicio | `docker-compose.yml` | `productos-db`, `pedidos-db` |
| Docker | Dockerfile por componente | `*/Dockerfile` | `docker compose up --build` |
| API Gateway | Nginx (local) / API Gateway (AWS) | `frontend/nginx.conf`, sección 13 | Prueba 7 vía `/api/...` |
| EC2 | Guía de despliegue | Sección 13.3 | — |
| Base de datos cloud | RDS PostgreSQL | Sección 13.4 | — |
| GitHub | `.gitignore` por componente, sin secretos | `.gitignore` (raíz, `frontend/`, `backend/`) | Revisión del repo |

## 12. Guion de presentación (5-10 min)

1. Mostrar el diagrama de arquitectura (sección 2).
2. Login con Cognito (Authenticator) — registrar o usar un usuario existente.
3. Mostrar el Dashboard con el email y el rol leídos del JWT.
4. Mostrar la página de Productos (como `USER`: solo lectura; como `ADMIN`: crear uno).
5. Crear un pedido y mostrar el total calculado.
6. Abrir DevTools → Network → mostrar el header `Authorization: Bearer ...` en una petición.
7. `curl` sin token → mostrar `401`.
8. Con un usuario `USER`, intentar `POST /api/productos` desde Postman → mostrar `403`.
9. Mostrar Docker Desktop con los 5 contenedores corriendo.
10. Mostrar el código de `SecurityConfig` explicando la validación de firma/issuer/expiración/roles.
11. Explicar en 1-2 minutos qué variables cambian para desplegar en AWS (sección 13).

## 13. Despliegue en AWS

> Esta guía indica **qué crear y qué variables cambiar**. No inventa IDs, ARNs ni URLs: cada uno se obtiene desde la consola de AWS al momento de crear el recurso, tal como se explicó en la sección 7 para Cognito.

### 13.1 Cognito

Ya creado en la sección 7 (`User Pool`, `App Client`, grupos `USER`/`ADMIN`). Solo debes:

- Agregar la URL de Amplify Hosting a **Allowed callback URLs** del App Client.

### 13.2 Amplify Hosting (frontend)

1. AWS Amplify → **Host your web app** → conecta el repositorio de GitHub del frontend.
2. En **Environment variables**, configura:
   - `VITE_API_URL` = Invoke URL de tu API Gateway (sección 13.4)
   - `VITE_COGNITO_REGION`, `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_USER_POOL_CLIENT_ID`
3. Build command: `npm run build`. Output directory: `dist`.
4. Deploy. Amplify entrega una URL pública — agrégala a los callback URLs de Cognito (paso anterior).

### 13.3 EC2 (backend)

Para cada microservicio (`productos-service`, `pedidos-service`):

1. Lanzar una instancia EC2 (ej. Amazon Linux 2023, t3.micro).
2. **Security Group**: abrir el puerto de la app (8081 u 8082) solo desde el Security Group de API Gateway/VPC Link, y el puerto 22 solo desde tu IP.
3. Instalar Docker en la instancia (`sudo yum install docker -y && sudo systemctl start docker`).
4. Copiar el proyecto (`git clone` del repo backend) y ejecutar:
   ```bash
   docker build -t productos-service ./productos-service
   docker run -d -p 8081:8081 \
     -e DB_HOST=<endpoint-rds> \
     -e DB_NAME=productos_db \
     -e DB_USERNAME=<usuario> \
     -e DB_PASSWORD=<password> \
     -e COGNITO_ISSUER_URI=<issuer-de-la-sección-7.3> \
     -e CORS_ALLOWED_ORIGINS=<url-de-amplify> \
     productos-service
   ```
5. Repetir para `pedidos-service`, agregando además `PRODUCTOS_SERVICE_URL=http://<IP-privada-EC2-productos>:8081`.

### 13.4 API Gateway

1. Crear una **HTTP API** (más simple que REST API para este caso).
2. Crear un **JWT Authorizer**:
   - Issuer URL: el mismo `COGNITO_ISSUER_URI` de la sección 7.3.
   - Audience: el **App Client ID** de Cognito.
3. Crear las rutas e integraciones:
   - `ANY /api/productos/{proxy+}` → integración HTTP hacia `http://<IP-privada-EC2-productos>:8081/api/productos/{proxy}`
   - `ANY /api/pedidos/{proxy+}` → integración HTTP hacia `http://<IP-privada-EC2-pedidos>:8082/api/pedidos/{proxy}`
4. Adjuntar el JWT Authorizer a ambas rutas.
5. Desplegar (`Deploy`) y copiar la **Invoke URL** → esa es tu `VITE_API_URL`.

> Nota: si las instancias EC2 están en una VPC privada, API Gateway debe conectarse mediante un **VPC Link**; documentar esa configuración excede el alcance de esta guía y depende de la VPC concreta que se use — indicarlo explícitamente en la presentación si no se dispone de esos datos.

### 13.5 Base de datos cloud

Usar **Amazon RDS para PostgreSQL** (una instancia `db.t3.micro` es suficiente), creando una base de datos lógica por microservicio (`productos_db`, `pedidos_db`) dentro de la misma instancia, o dos instancias separadas si se prefiere aislamiento total. Apuntar `DB_HOST`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` de cada microservicio al endpoint de RDS correspondiente.

### 13.6 Resumen de variables que cambian (LOCAL → AWS)

| Variable | Local | AWS |
| --- | --- | --- |
| `VITE_API_URL` | *(vacío, usa Nginx)* | Invoke URL de API Gateway |
| `DB_HOST` | `productos-db` / `pedidos-db` (contenedor) | Endpoint de RDS |
| `PRODUCTOS_SERVICE_URL` | `http://productos-service:8081` | `http://<IP-privada-EC2>:8081` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | URL de Amplify Hosting |
| `COGNITO_ISSUER_URI` | Igual en ambos entornos (mismo User Pool) | Igual |

La lógica de negocio y de seguridad (Java, React) **no cambia**: solo configuración.
