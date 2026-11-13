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
