# Compilazione e avvio in STS4

Per la nuova applicazione con finestra propria seguire [DESKTOP.md](DESKTOP.md).
Le istruzioni seguenti riguardano l'avvio web originale.

Le configurazioni `.launch` incluse usano il runtime Java 21 registrato in questo
workspace con il nome `jre` (quello incluso in Spring Tools). Il codice viene
compilato per Java 17. Java 27 non e compatibile con le attuali librerie dei test.

1. Selezionare il progetto `pswmanager-secure` e premere F5.
2. Eseguire Maven > Update Project (Alt+F5).
3. Clic destro su `Desktop - Maven install.launch` > Run As > Maven build.
   Esegue `clean install`, senza profili aggiuntivi e con i test abilitati.
4. Dopo BUILD SUCCESS, clic destro su `STS - Avvia pswmanager-secure.launch`
   > Run As > Spring Boot App.
5. Aprire http://localhost:6969. Per arrestare usare Terminate nella Console.

Le stesse configurazioni sono disponibili in Run > Run Configurations, nelle
categorie Maven Build e Spring Boot App. Se STS non mostra Run As sul file,
selezionarle da questa finestra e premere Run.

Per usare invece il comando generico Run As > Maven install sul progetto,
selezionare il runtime `jre` (Java 21) come predefinito in
Window > Preferences > Java > Installed JREs. Nella scheda JRE delle
configurazioni Maven e Spring Boot si puo anche selezionarlo singolarmente.

Il database viene salvato in `passwords.db` nella directory del progetto.
Una sola istanza alla volta puo occupare la porta 6969.
