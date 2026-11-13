# FF Remote Lab 2.0

Applicazione Android di laboratorio per dispositivi **di proprieta' o autorizzati**, controllata da Kali Linux tramite ADB Wi-Fi.

- Associazione ADB effettuata dal proprietario sul telefono.
- Ogni dispositivo mantiene una propria autorizzazione, revocabile.
- Comandi limitati e visibili, solo quando la sessione e' attiva sullo schermo.
- Funzioni: ping, stato, messaggio, vibrazione, beep, revoca; schermo tramite scrcpy esterno.
- Nessun accesso nascosto a messaggi, foto, microfono o file.

**Build:** in GitHub Actions apri **Build Android Debug APK**; l'APK apparira' come artifact quando il workflow termina con successo.

Per istruzioni in italiano, consulta [`README_IT.md`](README_IT.md).

> Il debug wireless di Android deve essere attivato e associato una prima volta sul telefono. Questo repository non consente accesso automatico a telefoni sconosciuti.
