import { Amplify } from "aws-amplify";

// Configuración de Amazon Cognito leída desde variables de entorno (VITE_*), para que
// el mismo código funcione en LOCAL y en AWS cambiando solo el archivo .env.
Amplify.configure({
  Auth: {
    Cognito: {
      region: import.meta.env.VITE_COGNITO_REGION,
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
      userPoolClientId: import.meta.env.VITE_COGNITO_USER_POOL_CLIENT_ID,
    },
  },
});
