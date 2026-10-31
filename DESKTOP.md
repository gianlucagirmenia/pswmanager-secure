# Password123 per Windows

## Avvio da STS4

1. Selezionare `pswmanager-secure`, premere F5 e poi Maven > Update Project (Alt+F5).
2. Run > Run Configurations > Maven Build > **Desktop - Maven install** > Run.
3. Dopo BUILD SUCCESS: Java Application > **Desktop - Password123** > Run.

Le configurazioni usano il runtime Java 21 chiamato `jre` nel workspace attuale.
Su un altro PC selezionare un JDK 21 nella scheda JRE. Java 27 non e compatibile
con le attuali librerie Mockito dei test. Il bytecode del progetto resta Java 17.

La finestra mostra l'interfaccia web tramite JavaFX WebView. Spring Boot ascolta
solo su 127.0.0.1 e su una porta libera scelta a ogni avvio. Chiudere la finestra
arresta anche Spring Boot. Una seconda istanza sullo stesso archivio viene rifiutata.
L'avvio web originale da `PswmanagerApplication` resta disponibile separatamente.

## Database e primo avvio

Il database desktop e `%LOCALAPPDATA%\PswManager\passwords.db`, i log sono nella
sottocartella `logs`. Il pulsante **Archivio** mostra il percorso effettivo.
Per una directory alternativa impostare nella scheda Arguments > VM arguments:

```text
-Dpswmanager.data-dir=C:\percorso\archivio
```

Se il database non esiste, un dialogo permette di creare un archivio nuovo oppure
selezionare un database esistente. Per conservare le credenziali attuali scegliere
**Importa database esistente** e selezionare il `passwords.db` nella cartella del
progetto. La copia viene prodotta da SQLite, conserva i dati cifrati e la master
password e non sostituisce l'originale. I due archivi diventano indipendenti.
Non vengono copiati automaticamente dati personali nel pacchetto Windows.

Selezionando un archivio nuovo, la pagina iniziale permette di impostare la nuova
master password. Una password dimenticata non puo essere recuperata. La cifratura
delle credenziali rimane quella esistente; non viene aggiunta la cifratura di tutti
i metadati del database. Usare l'esportazione cifrata per i backup.

Gli export aprono il dialogo Salva; Annulla non scrive file. La copia negli appunti
usa gli appunti Windows e li pulisce dopo 30 secondi o alla chiusura, se contengono
ancora lo stesso testo. Import da file, conferme e avvisi rimangono disponibili.
I link esterni vengono aperti nel browser di sistema solo dopo conferma.
Le icone/font caricati da CDN e il controllo HIBP continuano a richiedere Internet.

## Generazione dell'eseguibile

Run > Run Configurations > Maven Build > **Desktop - Crea EXE Windows** > Run.
Esegue `clean install` con il profilo `windows-desktop`, test inclusi, e poi
`jpackage --type app-image`. Il risultato e:

```text
target\desktop\Password123\Password123.exe
```

Si deve distribuire **tutta la cartella Password123**, non solo il file exe.
Include il runtime Java usato da Maven: non serve installare Java sul PC destinatario.
Questo e un programma portatile in una cartella, non un installer con procedura
guidata. Il database resta nella cartella dati dell'utente anche dopo gli aggiornamenti.

Lo script `packaging/windows.ps1` cerca jpackage in JPACKAGE_HOME, nel runtime,
in JAVA_HOME e nel PATH. Su questo PC usa anche il JDK 27 gia installato come
strumento di confezionamento, includendo comunque il runtime Java 21 di Maven.
Su altri PC impostare JPACKAGE_HOME a un JDK completo 21 o successivo e riavviare STS4.
Chiudere l'eseguibile prima di ricreare il pacchetto con clean install.

## Verifiche da STS4

I test comprendono la cifratura/import esistenti e la nuova gestione archivio:
blocco seconda istanza, rilascio del blocco, copia con transazioni WAL, mancata
sovrascrittura e rifiuto di database estranei o mancanti.

Per una prova manuale usare una directory dati temporanea tramite la proprieta
indicata sopra. Verificare: creazione e riapertura archivio, login errato/corretto,
creazione/modifica/eliminazione credenziale, mostra/copia, export e annullamento,
import backup, chiusura e successivo riavvio, secondo avvio contemporaneo.
Ripetere il controllo dall'EXE, spostando tutta la cartella in un percorso con spazi.
La verifica grafica e della distribuzione va eseguita dopo la compilazione in STS4.

Riferimenti: [JavaFX WebEngine](https://openjfx.io/javadoc/21/javafx.web/javafx/scene/web/WebEngine.html),
[pacchetti Windows](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)
e [rilasci JavaFX 21](https://gluonhq.com/products/javafx/openjfx-21-release-notes/).
