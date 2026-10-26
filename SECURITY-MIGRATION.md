# Security migration

Questa versione non contiene `passwords.db` o log reali.

## Prima di sostituire la versione esistente

1. Arrestare completamente l'applicazione originale.
2. Copiare `passwords.db` in una posizione di backup non sincronizzata pubblicamente.
3. Conservare temporaneamente anche una copia del vecchio eseguibile.
4. Avviare la nuova versione dalla stessa directory del database e accedere con la master password esistente.

Al primo accesso valido:

- l'hash SHA-256 legacy della master password viene aggiornato a PBKDF2-HMAC-SHA-256;
- viene creato un salt casuale per la derivazione della nuova chiave AES-256;
- ogni record CBC decifrato con successo viene ricifrato in AES-256-GCM (`v2$...`);
- un record corrotto interrompe la migrazione e non viene sovrascritto come testo in chiaro.

Non cancellare il backup finche non sono state controllate manualmente alcune credenziali e non e stato verificato un ciclo completo export/import.

## Modifiche di sicurezza

- AES-256-GCM autenticato per tutte le nuove cifrature.
- PBKDF2-HMAC-SHA-256 con 600.000 iterazioni e salt casuali.
- Compatibilita di sola lettura con il precedente AES-CBC durante la migrazione.
- Nessun segreto nei log o nei metodi `toString()`.
- Password caricate singolarmente dalla UI solo quando mostrate o copiate.
- Token CSRF sulle operazioni mutanti, eliminazione e logout via POST.
- Controllo centralizzato della sessione e security headers.
- Server limitato a `127.0.0.1`, cookie HttpOnly/SameSite Strict e timeout di 15 minuti.
- Correzione del ripristino cifrato e della sovrascrittura dei duplicati.

## Verifica

Sono inclusi test per round-trip GCM, nonce casuali, rilevamento delle manomissioni,
compatibilita CBC e ripristino della password originale da backup.

Eseguire:

```powershell
mvn clean test
```

La JVM usata durante lo sviluppo ha generato correttamente tutte le 58 classi principali.
Nell'ambiente isolato Codex, la fase di compilazione dei test ha incontrato un errore
Windows `AccessDeniedException` interno a `jdk.zipfs` durante la chiusura di JAR nella
cache Maven. Non e un errore diagnostico del sorgente, ma i test devono essere rieseguiti
nel normale workspace STS prima di usare dati reali.
