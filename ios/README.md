# FF Remote Lab - iOS starter

Questo progetto iOS e' **un prototipo sorgente**, non un'IPA pronto da installare e non fornisce ancora streaming a Kali.

- App SwiftUI con selettore Apple ReplayKit.
- Estensione Broadcast Upload che riceve i campioni video dalla sessione avviata con consenso esplicito.
- Nessuna rete / backend: i fotogrammi non sono trasmessi, salvati o elaborati.
- Nessun controllo remoto di touch o schermo: Apple non lo consente alle normali app di terze parti.

## Compilazione (macOS con Xcode)

1. Installa Xcode e [XcodeGen](https://github.com/yonaskolb/XcodeGen).
2. Nella cartella `ios/`, esegui `xcodegen generate`.
3. Apri `FFRemoteLab.xcodeproj`, configura un team Apple per la firma dell'app e dell'estensione.
4. Compila e installa con Xcode su un iPhone di test.
5. Tocca il pulsante di condivisione dello schermo di sistema.

**Importante:** per mostrare il video su Kali occorrono ancora un trasporto autenticato, una sessione revocabile e un ricevitore desktop. Questa versione non li include.

## Visualizzare subito un iPhone su Kali: AirPlay + UxPlay (senza app FF Remote Lab)

Mentre l'estensione iOS per FF Remote Lab non trasmette ancora a Kali,
puoi gia' **visualizzare** lo schermo dell'iPhone in una finestra su Kali con
[UxPlay](https://github.com/FDH2/UxPlay), un ricevitore AirPlay open source.

Su Kali Linux, quando il pacchetto e' presente nei repository:

```bash
sudo apt update
sudo apt install uxplay
uxplay -n "FF Remote Lab Kali" -pin -reg
```

Su iPhone: Centro di Controllo > Duplica schermo > **FF Remote Lab Kali**,
quindi conferma l'eventuale PIN mostrato da UxPlay.

Requisiti: stesso segmento Wi-Fi/LAN, discovery Bonjour/mDNS funzionante e
connessioni consentite dal firewall. Kali in VirtualBox potrebbe richiedere
una **scheda di rete con bridge**, perche' NAT spesso impedisce la discovery
AirPlay da iPhone.

Questo e' **solo mirroring avviato dall'iPhone**, non controllo remoto del touch,
non ADB e non una connessione che parte di nascosto.

La versione UxPlay disponibile su Kali puo' dipendere dai repository attivi.
