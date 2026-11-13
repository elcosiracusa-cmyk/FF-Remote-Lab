# FF Remote Lab — compatibilita' Android, iOS e sistemi desktop

## Scopo
Un'unica interfaccia desktop per la gestione **consensuale** di dispositivi mobili. Il programma rileva la piattaforma e usa l'adattatore supportato: non tenta di far girare ADB su iOS.

## I due livelli da distinguere
**Computer di controllo (host):** ADB e' disponibile per Windows, macOS e Linux. Un client scritto in Python puo' invocare ADB su tutti e tre, se le Platform Tools sono installate.

**Telefono controllato (target):** ADB richiede il demone `adbd` e il debug Android. iOS non fornisce quel demone e non accetta comandi ADB.

## Funzioni consentite per piattaforma

| Funzione | Android (stato) | iOS (stato) |
| --- | --- | --- |
| Associazione al PC | ADB USB o wireless, approvazione sul telefono (disponibile) | Abbinamento mediante flussi Apple; non equivale ad ADB (da progettare) |
| Schermo su PC | scrcpy dopo pairing ADB (disponibile) | Condivisione schermo mediante ScreenCaptureKit e conferma dell'utente (non implementato) |
| Controllo mediante mouse e tastiera | scrcpy con ADB (disponibile) | **Non accessibile a normali app iOS di terze parti** |
| Comandi FF Remote Lab | Sessione attivata e visibile sul telefono (disponibile) | Solo comandi nell'app esplicitamente autorizzati, da implementare |
| Diagnostica | ADB, compatibilmente con permessi Android | Dati limitati alle API iOS autorizzate, da implementare |
| Avvio automaticamente dello schermo | Possibile dal client desktop per dispositivi ADB gia' autorizzati | Non e' possibile avviare condivisione schermo nascosta alla sola installazione dell'app |

## Proposta di architettura 3.0

```text
FF Remote Lab Desktop (Windows / Linux / macOS)
  |
  +-- Adattatore Android: ADB + scrcpy + app Android FF Remote Lab
  |
  +-- Adattatore iOS: app Swift + condivisione schermo consentita
  |     +-- acquisizione tramite API Apple di sistema
  |     +-- streaming crittografato verso sessione autorizzata
  |     +-- interruzione e revoca sempre disponibili
  |
  +-- Registro comandi e connessioni, per singolo dispositivo
```

Requisiti di sicurezza: autorizzazione esplicita per ogni telefono, indicatori visibili di sessione, fine sessione e revoca, nessuna credenziale condivisa universale, comunicazioni autenticate e cifrate. L'installazione da sola non implica diritto di controllare il telefono.

**iPhone:** ScreenCaptureKit documenta lo screen streaming con una scelta esplicita del contenuto e il permesso di registrazione. Non concede a un'app di controllare touch e interfaccia di altre app. MDM e' destinato all'ambito aziendale e supporta un insieme circoscritto di comandi di gestione di dispositivi iscritti; non e' un sostituto di scrcpy. FaceTime consente il controllo remoto in certe regioni previa autorizzazione, ma la funzione non e' attualmente disponibile nell'Unione europea secondo Apple.

## Riferimenti ufficiali
- Android Debug Bridge: https://developer.android.com/tools/adb
- Apple ScreenCaptureKit: https://developer.apple.com/documentation/ScreenCaptureKit
- Apple MDM: https://developer.apple.com/documentation/devicemanagement/commands-and-queries
- Apple FaceTime remote control e limiti regionali: https://support.apple.com/en-afri/guide/iphone/iph5d70f34a3/26/ios/26

## Stato di implementazione
- Android: sorgenti gia' presenti nel repository.
- iOS: **specifica di fattibilita' soltanto**. Non e' presente un progetto Swift, un backend streaming, un IPA o una build di TestFlight.
- Dashboard multipiattaforma: proposta architetturale, non ancora distribuita.
