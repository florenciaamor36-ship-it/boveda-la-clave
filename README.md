# Bóveda La Clave (Android)

Prototipo nativo para guardar copias privadas de fotos y videos en el almacenamiento interno cifrado de Android, protegido por PIN. La pantalla de acceso incorpora la animación de disco: error con temblor; PIN correcto con giro y tilde.

## Qué hace esta primera versión

- Crea un PIN de 6 a 10 dígitos y almacena una derivación PBKDF2-HMAC-SHA256 con salt (no guarda el PIN en texto).
- Cifra cada archivo importado con AES-256-GCM usando una clave AES no exportable del Android Keystore.
- Importa imágenes y videos con el selector de documentos de Android; almacena una copia cifrada en el sandbox privado de la app.
- Muestra los archivos importados y permite abrir o borrar cada uno. Al abrir, descifra una copia temporal privada para compartirla con el visor de Android; la caché temporal se limpia al cerrar/bloquear y al iniciar la app.
- Incluye botón de cerrar, bloqueo automático tras dejar la app en segundo plano y borrado de toda la bóveda.
- No usa red ni envía archivos a un servidor. Desactiva el backup de Android y bloquea capturas en la pantalla de la app.

## Importante: límites de seguridad

Es una versión inicial para pruebas, no una auditoría de seguridad. El PIN solo se aplica a la bóveda, no reemplaza el PIN/huella del teléfono. Los originales permanecen en Fotos/Galería; esta app importa una copia y no los elimina. Al abrir un archivo, Android puede entregarlo al visor elegido por el usuario. Borrar la app o perder el material protegido por Android Keystore puede volver irrecuperables los archivos. No uses todavía la APK de prueba como único lugar para guardar contenido irremplazable.

## Build

- Android Studio / Android SDK 35
- JDK 17
- Gradle 8.9 (el workflow usa gradle/actions/setup-gradle)

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

El workflow de GitHub Actions compila el APK debug y publica un artifact descargable en cada push y ejecución manual.

## Licencia

MIT para este proyecto. AndroidX Core es Apache-2.0. Las licencias de Android/SDK se aplican por separado.
