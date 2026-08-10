# App Android (WebView)

Wrapper nativo minimo attorno all'app web (`https://cercadocumenti.onrender.com`):
una `WebView` a schermo intero, più due integrazioni che una WebView non offre
gratis:

- **Upload file**: `onShowFileChooser` nel `WebChromeClient` apre il selettore
  file nativo quando l'app web mostra un `<input type="file">` (form di
  inserimento/modifica documento).
- **Download file**: l'app web normalmente scarica creando un blob JS e
  cliccando un link `<a download>` — dentro una WebView questo non salva
  nulla di reale sul dispositivo. Il frontend
  ([`documentsApi.ts`](../frontend/src/api/documentsApi.ts)) rileva la
  presenza di `window.AndroidDownloader` (iniettato solo qui, mai in un
  browser normale) e gli passa il file come base64; `MainActivity.kt` lo
  decodifica e lo salva davvero nella cartella Download del telefono via
  `MediaStore` (richiede Android 10+, `minSdk 29` — scelta deliberata per
  evitare la gestione dei permessi legacy pre-scoped-storage).

Nessun'altra logica: login, ricerca, condivisione ecc. funzionano perché sono
semplicemente la stessa pagina web, non reimplementati in Kotlin.

## Compilare

Serve JDK 17+ (già usato dal backend di questo repo) e una connessione a
Internet al primo build (Gradle scarica le dipendenze). L'SDK Android **non**
serve installarlo a mano: `local.properties` (non versionato, va creato da
te) deve solo puntare a una cartella SDK esistente — se non ne hai una,
`sdkmanager` del pacchetto "command line tools" di Android la crea:

```bash
# esempio - adatta i percorsi
echo "sdk.dir=C:/Android/Sdk" > local.properties

./gradlew.bat assembleDebug
# APK in: app/build/outputs/apk/debug/app-debug.apk
```

L'APK di debug è firmato con la chiave di debug standard di Android (va bene
per installarlo su un telefono, non per pubblicarlo su Play Store — per
quello serve una chiave di release dedicata, non inclusa qui).

## Installare sul telefono

1. Copia `app-debug.apk` sul telefono (cavo, email, drive...)
2. Apri il file dal telefono — Android chiederà di abilitare "Installa app
   da fonti sconosciute" per l'app che stai usando per aprirlo (Gestione
   file, Gmail, ecc.), va concesso una volta sola
3. Installa

## Se cambia l'URL dell'app web

`MainActivity.kt`, costanti `APP_HOST`/`START_URL` in fondo al file — poi
`./gradlew.bat assembleDebug` di nuovo.
