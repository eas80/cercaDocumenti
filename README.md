# documentstore

Backend Spring Boot per la gestione di documenti (id, nome, descrizione, contenuto,
data ultima modifica). Storage intercambiabile dietro un'unica interfaccia
(`DocumentRepository`): disco locale (default), Cloudinary, o — con una
guida, non ancora implementato — MongoDB, senza toccare service/controller.

## Run

```bash
mvn spring-boot:run
```

I documenti vengono salvati in `./data/documents` (configurabile in
`application.yml` con `documentstore.storage.disk.directory`).

## Test

```bash
mvn test
```

## Frontend

UI React (Vite + TypeScript) in [`frontend/`](frontend/README.md):

```bash
cd frontend
npm install
npm run dev
```

Apre su `http://localhost:5173`; in dev il proxy di Vite (`vite.config.ts`)
inoltra le chiamate `/api/...` al backend su `http://localhost:8080`, quindi
non serve configurare CORS in locale.

## App Android

Wrapper WebView nativo attorno al frontend deployato, con upload/download di
file funzionanti (non scontati dentro una WebView) — vedi
[`android/README.md`](android/README.md) per dettagli e istruzioni di build.
APK di debug pronto all'uso in `CercaDocumenti.apk` alla root (non
versionato — rigenerabile con `android/gradlew.bat assembleDebug`).

## Autenticazione

Tutte le API sotto `/api/documents/**` richiedono un token JWT (`Authorization:
Bearer <token>`), ottenuto via login. Nessuna auto-registrazione: gli utenti
sono un elenco fisso in configurazione.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo1234"}'
# -> {"token":"eyJ...","username":"demo"}

curl http://localhost:8080/api/documents \
  -H "Authorization: Bearer eyJ..."
```

Configurazione (env var, vedi `application.yml` per i nomi delle proprietà):

| Env var | Significato | Se non impostata |
|---|---|---|
| `DOCUMENTSTORE_AUTH_USERS` | `user1:pass1,user2:pass2` — elenco utenti | genera un singolo utente `admin` con password casuale, **loggata a ogni avvio** (cerca `AUTH:` nei log) |
| `DOCUMENTSTORE_JWT_SECRET` | chiave di firma dei token, minimo 32 byte | genera una chiave casuale per processo: tutti i token diventano non validi a ogni riavvio |
| `DOCUMENTSTORE_JWT_EXPIRATION_MINUTES` | durata del token (default 120) | — |

`/health` resta pubblico (serve per l'health check di Render, vedi sotto).

Il frontend gestisce login/logout da solo (pagina di login, token in
`localStorage`, header `Authorization` allegato automaticamente a ogni
chiamata, logout automatico se il backend risponde `401`) — non serve altra
configurazione lato frontend.

## API

Le API sotto richiedono tutte autenticazione (vedi sopra), tranne dove indicato.

### 1. GET /api/documents/{id} — recupera un documento per id

Restituisce il contenuto grezzo del file (`Content-Type` originale,
`Content-Disposition: attachment`). Metadati aggiuntivi nelle response header
`X-Document-Id`, `X-Document-Name`, `X-Document-Last-Modified`.

```bash
curl -i http://localhost:8080/api/documents/<id> -o downloaded.bin
```

### 2. GET /api/documents — ricerca

Parametri tutti opzionali, combinabili:

| Parametro | Tipo | Significato |
|---|---|---|
| `nameLike` | string | substring case-insensitive su nome |
| `descriptionLike` | string | substring case-insensitive su descrizione |
| `dateFrom` | `yyyy-MM-dd` | data ultima modifica >= |
| `dateTo` | `yyyy-MM-dd` | data ultima modifica <= |

```bash
curl "http://localhost:8080/api/documents?nameLike=fattura&dateFrom=2026-01-01&dateTo=2026-12-31"
```

Risposta: elenco di oggetti metadata (id, name, description, contentType,
sizeBytes, lastModifiedDate, owner, sharedWith) — senza il contenuto del
file. Restituisce solo i documenti tuoi o condivisi con te (vedi
"Proprietà e condivisione" sotto).

### 3. PUT /api/documents — inserisce un nuovo documento

`multipart/form-data`: `name` (obbligatorio), `description` (opzionale),
`file` (obbligatorio). L'id viene generato dal server.

```bash
curl -X PUT http://localhost:8080/api/documents \
  -F name="Fattura Gennaio" \
  -F description="Fattura mensile di gennaio" \
  -F file=@fattura.pdf
```

Risposta `201 Created`, header `Location: /api/documents/{id}`.

### 4. POST /api/documents/{id} — aggiorna un documento esistente

`multipart/form-data`, tutti i campi opzionali: `name`, `description`, `file`.
I campi omessi mantengono il valore precedente. 404 se l'id non esiste.

```bash
curl -X POST http://localhost:8080/api/documents/<id> \
  -F description="Fattura di gennaio, versione corretta" \
  -F file=@fattura-v2.pdf
```

### 5. DELETE /api/documents/{id} — elimina un documento

404 se l'id non esiste. Nessun corpo nella richiesta, `204 No Content` in
risposta.

```bash
curl -X DELETE http://localhost:8080/api/documents/<id> \
  -H "Authorization: Bearer <token>"
```

### 6. POST /api/documents/{id}/share — condivide un documento

Solo il proprietario può chiamarla (`403` altrimenti). Sostituisce
interamente l'elenco di condivisione con quello passato — per togliere una
condivisione, richiama con un elenco più corto (o vuoto).

```bash
curl -X POST http://localhost:8080/api/documents/<id>/share \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"usernames":["simona"]}'
```

`400` se uno degli username non è tra quelli configurati
(`DOCUMENTSTORE_AUTH_USERS`) — l'elenco valido è consultabile con
`GET /api/auth/users` (richiede solo autenticazione, non l'ownership di
nessun documento).

## Proprietà e condivisione

Ogni documento ha un `owner` (chi l'ha caricato) e un `sharedWith` (elenco di
altri utenti con accesso pieno). Le regole, applicate in
[`DocumentService`](src/main/java/com/example/documentstore/service/DocumentService.java):

- **Proprietario**: vede, cerca, scarica, modifica, elimina e condivide il
  documento. È l'unico che può cambiare `sharedWith`.
- **Utenti in `sharedWith`**: vedono, cercano, scaricano, modificano ed
  eliminano il documento come se fosse loro — ma non possono cambiare con chi
  è condiviso.
- **Tutti gli altri utenti**: il documento non compare nella loro ricerca;
  un accesso diretto per id restituisce `403`.
- **Documenti senza proprietario** (`owner: null`) — quelli caricati prima
  che esistesse questa funzionalità — sono visibili e modificabili da
  chiunque, invece di sparire per tutti: non essendoci nessuna informazione
  su chi li avesse caricati, trattarli come "pubblici" è l'unica scelta che
  non fa perdere l'accesso a nessuno.

Il frontend mostra un badge "Tuo" / "Di {utente}" per riga, e un bottone
"Condividi" (solo se sei il proprietario) che apre un elenco degli altri
utenti configurati da spuntare.

## Migrazione a MongoDB

L'accesso ai dati passa sempre per l'interfaccia
[`DocumentRepository`](src/main/java/com/example/documentstore/repository/DocumentRepository.java),
usata da `DocumentService` e mai implementata direttamente nel controller.
Oggi l'unica implementazione è
[`DiskDocumentRepository`](src/main/java/com/example/documentstore/repository/disk/DiskDocumentRepository.java),
attiva di default (`documentstore.storage.type=disk`).

Per passare a MongoDB, senza toccare service/controller:

1. Aggiungere la dipendenza al `pom.xml`:

   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-mongodb</artifactId>
   </dependency>
   ```

2. Configurare la connessione in `application.yml`:

   ```yaml
   documentstore:
     storage:
       type: mongo
   spring:
     data:
       mongodb:
         uri: mongodb://localhost:27017/documentstore
   ```

3. Creare un documento Spring Data e la relativa implementazione del
   repository (pacchetto `repository.mongo`), attiva solo quando
   `documentstore.storage.type=mongo`:

   ```java
   @org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
   record MongoDocument(
           @org.springframework.data.annotation.Id String id,
           String name,
           String description,
           byte[] content,
           String contentType,
           long sizeBytes,
           java.time.Instant lastModifiedDate) {
   }

   interface SpringDataMongoDocumentRepository
           extends org.springframework.data.mongodb.repository.MongoRepository<MongoDocument, String> {
   }

   @org.springframework.stereotype.Repository
   @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
           prefix = "documentstore.storage", name = "type", havingValue = "mongo")
   class MongoDocumentRepository implements DocumentRepository {

       private final SpringDataMongoDocumentRepository delegate;
       private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

       MongoDocumentRepository(SpringDataMongoDocumentRepository delegate,
                                org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
           this.delegate = delegate;
           this.mongoTemplate = mongoTemplate;
       }

       // findById/save: map DocumentEntity <-> MongoDocument, delegate.save/findById.
       // search(criteria): build a Query with Criteria.where("name").regex(nameLike, "i"),
       // "description" analogously, and "lastModifiedDate".gte/lte(...) for the date range,
       // then mongoTemplate.find(query, MongoDocument.class).
   }
   ```

4. Rimuovere (o lasciare spenta via profilo) `DiskDocumentRepository`
   impostando `documentstore.storage.type=mongo`: la `@ConditionalOnProperty`
   su entrambe le implementazioni garantisce che ne venga registrato un solo
   bean.

Per file molto grandi, in MongoDB conviene sostituire il campo `content`
con **GridFS** invece di un campo binario embedded: l'interfaccia
`DocumentRepository` non cambia, cambia solo come `MongoDocumentRepository`
legge/scrive i byte internamente.

## Storage su Cloudinary

Terza implementazione di `DocumentRepository`, oltre a disco e (guida)
MongoDB:
[`CloudinaryDocumentRepository`](src/main/java/com/example/documentstore/repository/cloudinary/CloudinaryDocumentRepository.java),
attiva con `documentstore.storage.type=cloudinary`.

**Design**: il *contenuto* dei documenti vive su Cloudinary (upload come
risorsa `resource_type=raw`, un servizio pensato per media ma che gestisce
bene anche file generici). Per la *ricerca* (nome/descrizione in like, range
di date) il repository scansiona file JSON locali, esattamente come fa
`DiskDocumentRepository` — verificato che la Search API di Cloudinary non è
un sostituto adeguato: supporta solo wildcard finali per singola parola
(`context.name:Fattura*`), non substring arbitraria come richiesto dalle API
di questa app (`*fattura*` genera un errore di sintassi della query).

Nome, descrizione, content-type, proprietario e condivisioni vengono **anche** salvati come `context` di
ogni asset su Cloudinary (non solo in locale). Questo non serve alla ricerca
(mai riletto per quello), ma è ciò che rende possibile la
**ricostruzione automatica dell'indice locale all'avvio**: se la directory dei
metadati locali viene svuotata (tipicamente un redeploy su una piattaforma
senza disco persistente — esattamente il sintomo "dopo ogni deploy non trova
più i file" osservato in produzione), `CloudinaryDocumentRepository`
interroga la Search API di Cloudinary per tutto ciò che ha il prefisso
`documentstore/` e rigenera i `.meta.json` mancanti, leggendo nome/
descrizione/content-type dal `context` salvato lì. I *contenuti* dei
documenti non sono mai stati a rischio (restano su Cloudinary
indipendentemente da cosa succede al container Render); era solo l'indice
locale usato per cercarli e servirli a sparire.

**Configurazione** (env var, vedi `application.yml` per i nomi delle
proprietà):

| Env var | Significato |
|---|---|
| `DOCUMENTSTORE_STORAGE_TYPE` | `cloudinary` per attivare questo backend |
| `DOCUMENTSTORE_STORAGE_CLOUDINARY_CLOUD_NAME` | Cloud name Cloudinary (in cima alla loro dashboard) |
| `DOCUMENTSTORE_STORAGE_CLOUDINARY_API_KEY` | API Key |
| `DOCUMENTSTORE_STORAGE_CLOUDINARY_API_SECRET` | API Secret — **mai committarla**, solo env var |
| `DOCUMENTSTORE_STORAGE_CLOUDINARY_METADATA_DIRECTORY` | dove salvare i JSON di metadati (default `./data/cloudinary-metadata`) |

**Verificato empiricamente** (non solo dedotto dalla documentazione, che su
alcuni punti è incompleta o addirittura contraddittoria fra endpoint diversi)
contro un vero account Cloudinary, incluso da
[`CloudinaryDocumentRepositoryIT`](src/test/java/com/example/documentstore/repository/cloudinary/CloudinaryDocumentRepositoryIT.java)
(gira solo se `DOCUMENTSTORE_CLOUDINARY_API_SECRET` è nell'ambiente — skip
automatico altrimenti, quindi `mvn test` resta verde senza credenziali):

- Caricando un `byte[]` senza nome file (il nostro caso: `MultipartFile` in
  memoria), Cloudinary **non** aggiunge estensioni a sorpresa al `public_id`
  — con un file reale invece sì (l'estensione del file originale viene
  accodata). Per questo il repository carica sempre `byte[]`, mai un `File`.
- L'URL di download **senza numero di versione non è affidabile** (torna
  `404`): il `secure_url` restituito dall'upload (con versione) va salvato e
  riusato per sempre, non ricostruito a mano.
- Il campo `context` nella risposta ha una forma **diversa a seconda
  dell'endpoint**: annidato sotto `custom` nel lookup di una singola risorsa
  (Admin API), ma piatto nella Search API. Il codice gestisce entrambe le
  forme.
- La ricerca per wildcard sul `context` supporta solo `parola*` (prefisso di
  singola parola/token), non `*parola*` (substring, errore di sintassi) — per
  questo la ricerca dell'app resta locale, Cloudinary è usato solo per
  ricostruire l'indice, non per interrogarlo direttamente a ogni richiesta.
- La Search API ha un **ritardo di indicizzazione** di qualche secondo dopo
  un upload (un test che caricava e cercava subito falliva in modo
  intermittente finché non è stato aggiunto un retry) — irrilevante in
  pratica: un redeploy avviene sempre ben dopo l'upload originale, mai a
  ridosso di pochi secondi.
- Autenticazione via HTTP Basic (`api_key:api_secret`) funziona anche per
  upload/destroy oltre che per le sole letture Admin API — comodo per
  debug con `curl`, non rilevante per l'app (l'SDK Java firma le richieste
  per conto suo).

**Nota sui documenti caricati prima di questo fix**: la prima versione di
questo backend non salvava affatto `context` su Cloudinary (bug corretto in
questo stesso commit). I documenti caricati prima di allora, se ricostruiti
dall'indice, torneranno con un nome segnaposto (il loro id) e senza
descrizione/content-type — quell'informazione non era recuperabile perché non
era mai stata salvata da nessuna parte se non nel disco locale poi perso. Da
qui in avanti, invece, sopravvivono a qualsiasi redeploy.

## Docker (backend)

Il [`Dockerfile`](Dockerfile) alla root è multi-stage: build con l'immagine
Maven ufficiale, runtime su una JRE 17 slim, utente non-root. Espone la porta
letta da `$PORT` (default `8080`, vedi `application.yml`).

```bash
docker build -t documentstore-backend .
docker run --rm -p 8080:8080 -e DOCUMENTSTORE_AUTH_USERS=demo:demo1234 documentstore-backend
curl http://localhost:8080/health
```

Lo storage di default nell'immagine è `/data/documents`
(`DOCUMENTSTORE_STORAGE_DISK_DIRECTORY`): se monti un volume/disco su `/data`
i documenti sopravvivono ai riavvii, altrimenti è storage effimero del
container (persa ad ogni `docker run`/redeploy) — va benissimo per provare
l'immagine, non per dati che contano.

> Nota: in questo ambiente di sviluppo Docker non era disponibile, quindi il
> Dockerfile non è stato eseguito localmente — segue però un pattern standard
> (build Maven ufficiale + runtime JRE separato) e usa lo stesso `pom.xml` già
> validato da `mvn test`. Vale la pena una build di prova (`docker build`)
> prima del primo deploy.

## Deploy su Render

Il file [`render.yaml`](render.yaml) alla root definisce entrambi i servizi
come Blueprint: su Render, **New > Blueprint**, seleziona questo repository.

- `documentstore-backend`: Web Service Docker (usa il `Dockerfile` sopra).
- `documentstore-frontend`: Static Site (`frontend/`, `npm ci && npm run build`,
  pubblica `frontend/dist`).

Entrambi i servizi hanno una variabile d'ambiente marcata `sync: false`
(Render la lascia vuota e te la chiede in dashboard) perché c'è una dipendenza
circolare fra i due URL, noti solo dopo il primo deploy:

1. Lancia il Blueprint. Render assegna un URL a entrambi i servizi (es.
   `https://documentstore-backend-xxxx.onrender.com` e
   `https://documentstore-frontend-xxxx.onrender.com`).
2. Sul servizio **backend**, imposta `DOCUMENTSTORE_CORS_ALLOWED_ORIGINS`
   all'URL del frontend (senza slash finale) e salva — Render lo riavvia da
   solo.
3. Sul servizio **frontend**, imposta `VITE_API_BASE_URL` all'URL del
   backend e triggera un **Manual Deploy**: essendo una static site, il
   valore viene "cotto" nel bundle in fase di build, quindi non basta
   riavviare, serve una rebuild.
4. `DOCUMENTSTORE_AUTH_USERS` è impostata **sia** in `render.yaml` **sia**
   come `ENV` nel `Dockerfile` (`simona:simona,antonio:antonio`). Il secondo
   è quello davvero in vigore: durante questo progetto le variabili
   d'ambiente impostate da dashboard per questo servizio non sono mai
   arrivate al container in modo affidabile (causa mai isolata con certezza
   — stesso sintomo capitato prima con il CORS), quindi si è scelto di
   "cuocerle" nell'immagine come già fatto per l'origine CORS. **Compromesso
   consapevole**: username e password restano in chiaro nella cronologia
   git di un repo pubblico — accettabile solo perché sono credenziali
   giocattolo per un progetto personale, da non fare se questo backend
   dovesse mai gestire qualcosa di sensibile (in quel caso: password diverse
   e più forti, o risolvere davvero il problema delle env var con Render,
   es. provando i loro **Secret Files** invece delle Environment Variables).
   Se in futuro una vera variabile d'ambiente dovesse arrivare al container,
   quella vince comunque su quella cotta nell'immagine.
   `DOCUMENTSTORE_JWT_SECRET` resta invece `sync: false` (solo dashboard, non
   nel Dockerfile): senza, ogni riavvio invalida le sessioni attive, fastidioso
   ma non un problema di sicurezza — se vuoi sessioni stabili tra i redeploy,
   impostala con una stringa casuale di almeno 32 caratteri.
5. Storage attivo: **Cloudinary**, anche questo cotto nel `Dockerfile`
   (`DOCUMENTSTORE_STORAGE_TYPE`, `DOCUMENTSTORE_STORAGE_CLOUDINARY_*`) dopo
   la terza conferma dello stesso problema di propagazione delle env var da
   dashboard (CORS, poi auth, poi questo). A differenza delle credenziali
   sopra, qui si tratta di un **vero segreto di un servizio terzo a
   pagamento**: chiunque legga la cronologia di questo repo pubblico potrebbe
   usarlo per caricare file o consumare la quota dell'account Cloudinary
   collegato. Scelta consapevole per sbloccare la funzionalità subito;
   valutare seriamente di risolvere il problema con il supporto Render (le
   prove raccolte in questo progetto — variabile impostata e salvata ma
   assente dal processo, per tre variabili diverse in tre momenti diversi —
   sono un buon punto di partenza per un ticket) e poi rigenerare/ruotare
   questa API key su Cloudinary una volta spostata fuori dal Dockerfile.

Storage: con `documentstore.storage.type=cloudinary` attivo, il *contenuto*
dei documenti è al sicuro su Cloudinary indipendentemente da Render. I
*metadati* (nome, descrizione, ricerca) vivono su disco in
`DOCUMENTSTORE_STORAGE_CLOUDINARY_METADATA_DIRECTORY` (default
`/data/cloudinary-metadata` nell'immagine Docker): senza un
[Render Disk](https://render.com/docs/disks) montato su `/data`, quell'indice
viene svuotato a ogni redeploy/riavvio (il piano free non supporta i Disk) —
ma **si ricostruisce da solo all'avvio** interrogando Cloudinary (vedi
sezione "Storage su Cloudinary" sopra), quindi non serve più un Disk per non
perdere i documenti, solo qualche secondo dopo ogni riavvio prima che
ricerca/elenco tornino completi. Con `documentstore.storage.type=disk`
(default se non impostato), invece, sia contenuto che metadati sono sullo
stesso disco effimero e si perdono insieme, senza nulla da cui recuperare.

### Login lento o che sembra bloccato

Sul piano gratuito, Render mette il servizio backend in stand-by dopo un
periodo di inattività: la prima richiesta dopo lo stand-by (tipicamente il
login) può impiegare **fino a un minuto** per il "risveglio" del container —
non è un errore, è il comportamento normale del piano free. Il frontend
mostra un avviso dopo qualche secondo di attesa per chiarirlo, invece di
lasciare l'utente a fissare un pulsante "Accesso in corso…" senza spiegazioni.
La ricostruzione dell'indice Cloudinary (sopra) gira in background *dopo*
che il server ha iniziato ad accettare richieste, apposta per non allungare
ulteriormente questo tempo di risveglio. L'unico modo per eliminare
completamente lo stand-by è passare a un piano Render a pagamento con
"instance type" sempre attivo.
