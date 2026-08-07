# documentstore

Backend Spring Boot per la gestione di documenti (id, nome, descrizione, contenuto,
data ultima modifica). Persistenza su file system oggi, pensata per essere
sostituita con MongoDB senza toccare service/controller.

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

## API

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
sizeBytes, lastModifiedDate) — senza il contenuto del file.

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
