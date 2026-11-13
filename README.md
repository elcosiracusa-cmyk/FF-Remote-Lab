# FF Remote Lab 3.0

Laboratorio di supporto remoto **autorizzato, visibile e revocabile**. Include Android funzionante tramite ADB, lancio automatico dello schermo via scrcpy per Kali e un **prototipo iOS non ancora completo**.

## Android 3.0: sessione persistente

- Il proprietario apre l'app e tocca **ABILITA SESSIONE CONTINUA**.
- Un **foreground service Android** mantiene attivo il ricevitore dei comandi quando l'interfaccia viene lasciata.
- **La notifica persistente mostra la sessione e include INTERROMPI**; nell'app resta il pulsante REVOCA SUBITO.
- Comandi da ADB autorizzato: `ping`, `message`, `vibrate`, `beep`, `status`, `stop`.
- I broadcast sono protetti dal permesso privilegiato `android.permission.DUMP`.
- Non si attiva il Debug wireless autonomamente, non si aggira la conferma ADB, non si avvia automaticamente dopo la prima installazione. Android puo' interrompere il servizio in condizioni di risparmio energetico o arresto forzato.
- Nessun accesso a messaggi privati, file, fotocamera, microfono o dati nascosti.

## APK Android

Apri **Actions > Build Android Debug APK** e scarica `FFRemoteLab-3-debug-apk` dall'ultima build verde. Estrai `app-debug.apk`, firmato con chiave debug. Non e' una versione Play Store.

Su Pixel: apri l'app e conferma la richiesta di notifiche, quindi tocca **ABILITA SESSIONE CONTINUA**. Potrai chiudere la schermata dell'app continuando a ricevere i comandi tramite ADB finche' il servizio rimane attivo.

## Kali Linux: apertura automatica dello schermo

Requisiti: adb, scrcpy, python3, connessione grafica e telefono gia' autorizzato in Debug wireless.

Dalla cartella del repository:

```bash
bash kali/install-autoscreen.sh
$HOME/.local/bin/ffremote-autoscreen allow 192.168.1.31
$HOME/.local/bin/ffremote-autoscreen watch
```

Al successivo accesso grafico a Kali, il monitor parte automaticamente. Quando ADB rileva il dispositivo consentito, lancia `scrcpy -s SERIALE -m 1024`. Se il telefono non e' gia' connesso via ADB, occorre usare `adb connect IP:PORTA_ATTUALE` (la porta puo' cambiare). Si puo' revocare l'apertura automatica:

```bash
$HOME/.local/bin/ffremote-autoscreen forget 192.168.1.31
```

Lo script non avvia scrcpy su telefoni non esplicitamente aggiunti all'elenco e non tenta l'abbinamento senza consenso.

## iPhone (prototipo)

In `ios/` sono presenti un progetto XcodeGen SwiftUI, una schermata ReplayKit e un'estensione che riceve campioni video **senza trasmetterli**. Attualmente **non** esiste un IPA installabile, ne' un ricevitore su Kali. Non e' ancora possibile vedere lo schermo di iPhone su Kali con questo progetto.

Su iOS la condivisione schermo richiede una scelta esplicita dell'utente tramite l'interfaccia di sistema. Un'app iOS ordinaria non puo' controllare via ADB il touch o altre app.

Vedi [dettagli iOS](ios/README.md) e [compatibilita' Android/iOS](docs/COMPATIBILITA_ANDROID_IOS.md).

## FF Remote Lab 4.0 - console desktop locale (anteprima)

E' disponibile `kali/ffremote-desktop.py`, una **console grafica locale** per gestire dispositivi Android abbinati e autorizzati via ADB. I pulsanti disponibili sono: aggiorna telefoni, connetti IP:PORTA, apri schermo via scrcpy dopo conferma, PING, STATUS, VIBRATE, BEEP, MESSAGE e STOP. Il registro locale indica quando vengono richiesti i comandi.

Su Kali Linux:

```bash
sudo apt install adb scrcpy python3-tk
git clone https://github.com/elcosiracusa-cmyk/FF-Remote-Lab.git
cd FF-Remote-Lab
python3 kali/ffremote-desktop.py
```

**Sicurezza e trasparenza**: la console non crea un'app invisibile, non nasconde indicatori Android, non disabilita le revoche e non elude le richieste di autorizzazione. La sessione Android continua a usare il servizio in primo piano della versione 3.0 e richiede l'abilitazione sul telefono. Il collegamento Wi-Fi funziona solo con ADB raggiungibile; il controllo via 4G/5G e una infrastruttura cifrata esterna non sono ancora implementati.

**iPhone**: nessuna app iOS completa; e' disponibile solo il prototipo SwiftUI/ReplayKit in `ios/`, e la duplicazione volontaria tramite AirPlay/UxPlay. ADB non supporta iPhone, ne' puo' controllare il touch iOS.

La dicitura **4.0** identifica per ora la console desktop sperimentale, non una nuova APK Android completa o un sistema di gestione remota via Internet.
