# FF Remote Lab 2.0

Applicazione Android di laboratorio per controlli **visibili, limitati e autorizzati** tramite Android Debug Bridge (ADB) Wi-Fi, da Kali Linux.

- Prima associazione ADB confermata dal proprietario su ogni telefono.
- Nessuna password universale o controllo nascosto.
- Sessione attivabile sul telefono; si interrompe se l'app esce dal primo piano.
- Comandi: `ping`, `message`, `vibrate`, `beep`, `status`, `stop`.
- Il controllo dello schermo completo richiede `scrcpy` (strumento esterno).
- Nessun accesso a messaggi, microfono, contatti, foto, file privati o Internet.

## Ottenere l'APK

Apri **Actions > Build Android Debug APK**. Dopo una build riuscita, scarica l'artifact **FFRemoteLab-2-debug-apk**. L'APK `app-debug.apk` è firmato con una chiave di debug Android: adatto ai test, non alla distribuzione Play Store.

## Installazione via Kali

1. Associa Android a Kali una volta sola: *Impostazioni > Opzioni sviluppatore > Debug wireless > Associa dispositivo*.
2. Da Kali: `adb pair IP:PORTA_ASSOCIAZIONE`, poi `adb connect IP:PORTA_CONNESSIONE`.
3. Installa l'APK: `adb install -r app-debug.apk`.
4. Apri **FF Remote Lab** sul telefono e premi **ABILITA COMANDI**.
5. Sul PC: `bash kali/ffremote.sh ping`, oppure `bash kali/ffremote.sh message "Ciao da Kali"`.
6. Revoca dal telefono con **REVOCA** o disattiva Debug wireless.

Il debug wireless è una funzione di Android e il suo abbinamento non può essere reso universale per tutti i telefoni. Usalo soltanto su dispositivi di proprietà o con consenso esplicito.
