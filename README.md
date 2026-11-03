# UpGrade

Agenda personal de Alberto para Android: horario, deberes, exámenes, eventos, gym, XP y rachas, modo Foco con bloqueo de apps, cierre del día y widget.

## Cómo sale la APK

Cada push a `main` compila la app en GitHub Actions y publica una versión en **Releases** con el archivo `UpGrade-vN.apk`.

## Instalar en el móvil (OPPO / ColorOS)

1. En el móvil, abre Chrome, entra en GitHub (cuenta BertoProyect) y ve a este repo → **Releases** → la última versión → descarga el `.apk`.
2. Ábrelo. Si pide permiso para instalar apps desconocidas desde Chrome, actívalo.
3. Abre UpGrade y sigue la pantalla **Configura UpGrade** (notificaciones, batería, accesibilidad, inicio automático).
4. Si la accesibilidad sale en gris: Ajustes → Apps → UpGrade → ⋮ → **Permitir ajustes restringidos**.

Desde la v4 todas las versiones van firmadas con la misma clave (secretos `UPGRADE_KEYSTORE_B64` y `UPGRADE_KEY_PASSWORD`), así que las actualizaciones se instalan encima y conservan los datos. Haz una copia en Ajustes → Copia de seguridad de vez en cuando.
