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

## FF Remote Lab 4.0 - connessione privata via Internet (codice sperimentale)

**Stato:** sono presenti server Kali, client Android e client iOS ReplayKit per lo streaming dello schermo, ma non sono ancora collaudati insieme su telefoni reali. Nessuna funzione nascosta. Uno smartphone Android e un iPhone per le prove; per il multi-device occorre estendere il protocollo e il pairing.

### 1. Configura Kali (server su rete privata)

Installa Tailscale su Kali seguendo la guida ufficiale: https://tailscale.com/download/linux
Accedi al tuo account Tailscale. Installa l'app Tailscale sul Pixel e sull'iPhone, accedi allo stesso `tailnet` e abilita la VPN quando serve.

Sul terminale Kali, nella cartella del repository:

```bash
python3 kali/remote4-server.py init
python3 kali/remote4-server.py show
python3 kali/remote4-server.py serve
```

`init` genera tre credenziali **differenti** (admin, Android e iPhone) nella directory personale `~/.config/ffremote-4/` con permessi privati. **Non inserirle nel repository e non inviare screenshot con i token.** Tieni aperto il terminale del server per il primo test; per chiudere premi Ctrl+C.

In un **secondo terminale Kali** configura Tailscale Serve:

```bash
tailscale serve --bg 8765
tailscale serve status
```

Usa solo **Tailscale Serve**, non `tailscale funnel` e non esporre pubblicamente TCP 8765 o ADB TCP 5555. Il relay ascolta esclusivamente su `127.0.0.1:8765` e Tailscale Serve fornisce HTTPS privato ai dispositivi appartenenti al tailnet.

Prendi dalla stampa di `tailscale serve status` l'URL HTTPS che termina in `.ts.net` e aggiungi `/` alla fine.

### 2. Android 4.0: prova Wi-Fi / 4G-5G

1. Installa la **build debug 4.0** dalla GitHub Action, quando verde.
2. Installa e attiva Tailscale sul Pixel, anche sotto rete mobile.
3. Apri FF Remote Lab, inserisci l'URL `https://...ts.net/` e **solo il token Android** ottenuto da `python3 kali/remote4-server.py show`. Salva.
4. Tocca **ABILITA SESSIONE CONTINUA**. Android mostra una notifica persistente con **INTERROMPI**.
5. Su Kali prova:

```bash
python3 kali/remote4-server.py status
python3 kali/remote4-server.py send PING
python3 kali/remote4-server.py status
python3 kali/remote4-server.py send VIBRATE
```

Per inviare un messaggio: `python3 kali/remote4-server.py send MESSAGE "Ciao dal laboratorio"`. Per disattivare la sessione: tasto nell'app o nella notifica, o `python3 kali/remote4-server.py send STOP`.

**Importante:** il relay consente solo i comandi sopra indicati. **Non trasporta ADB/scrcpy**: la visualizzazione completa e il controllo touch Android tramite scrcpy richiedono ancora un collegamento ADB separato, nelle condizioni autorizzate di Android.

### 3. iPhone: streaming con consenso via ReplayKit

Il progetto in `ios/` contiene l'app SwiftUI e un'estensione ReplayKit di caricamento video. L'app memorizza l'URL `https://...ts.net/` e il **solo token iPhone** in un App Group, poi presenta il selettore di trasmissione Apple. È necessaria una firma Apple reale e l'abilitazione dell'App Group `group.it.fflab.remoteios` per entrambi i target.

1. Compila `ios/` con Xcode su macOS. **Non è ancora disponibile un IPA firmato né un'installazione via Kali.**
2. Sul telefono configura e attiva Tailscale, poi URL e token iPhone nell'app.
3. Premi il selettore ReplayKit e autorizza **esplicitamente** la trasmissione.
4. Su Kali installa `python3-tk` e `python3-pil.imagetk` ed esegui:

```bash
sudo apt install python3-tk python3-pil.imagetk
python3 kali/ios-viewer.py
```

Il viewer legge l'ultimo frame JPEG in HTTPS attraverso il relay locale e lo mostra su Kali; trasmissione sperimentale a massimo 1 fotogramma/secondo, senza audio. Il frame scade quando iPhone interrompe la condivisione. Il traffico mobile e il consumo possono variare.

**Limite essenziale:** iOS non consente a un'app normale di controllare touch, altre app o i servizi di sistema come fa scrcpy su Android.

### 4. Limiti e prove necessarie

- Il servizio Android può essere fermato dal sistema, dalle impostazioni o dal proprietario.
- Se Tailscale sul telefono è disconnesso, il relay non riceve dati; al ripristino della VPN la sessione può riconnettersi.
- Non è prevista alcuna ripresa dello schermo nascosta o senza autorizzazione.
- La compilazione della build non dimostra che la rete 4G, ReplayKit e il flusso video siano stati collaudati su hardware reale.
- Chiudendo il terminale del server Kali la connessione termina; l'avvio automatico del server è da predisporre solo dopo i test locali.

Riferimenti: https://tailscale.com/docs/features/tailscale-serve e https://developer.apple.com/documentation/replaykit
