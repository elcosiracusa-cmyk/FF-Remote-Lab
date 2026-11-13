import SwiftUI
import ReplayKit

private let appGroup = "group.it.fflab.remoteios"
private let shared = UserDefaults(suiteName: appGroup)!

@main
struct FFRemoteLabApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @AppStorage("serverURL", store: shared) private var serverURL = ""
    @State private var iosToken = ""
    @State private var feedback = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("FF Remote Lab 4.0") {
                    Label("Streaming iPhone autorizzato", systemImage: "iphone.gen3")
                    Text("La condivisione funziona via Wi-Fi o 4G/5G, quando il telefono e Kali sono nella stessa rete privata Tailscale.")
                    Text("Nessun controllo remoto del touch: iOS non lo permette alle normali app.")
                }
                Section("Server privato") {
                    TextField("https://nome.tailnet.ts.net/", text: $serverURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                    SecureField("Token riservato iPhone", text: $iosToken)
                    Button("Salva e autorizza questo dispositivo") {
                        let url = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
                        let token = iosToken.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard url.hasPrefix("https://"), url.hasSuffix("/"),
                              !url.contains("@"), !url.contains("?"),
                              !url.contains("#"), token.count >= 32 else {
                            feedback = "Inserisci un URL HTTPS con / finale e il token iPhone."
                            return
                        }
                        shared.set(url, forKey: "serverURL")
                        shared.set(token, forKey: "iosToken")
                        iosToken = ""
                        feedback = "Configurazione salvata. Avvia la trasmissione dal selettore Apple."
                    }
                    Button("Revoca credenziali salvate", role: .destructive) {
                        shared.removeObject(forKey: "serverURL")
                        shared.removeObject(forKey: "iosToken")
                        serverURL = ""
                        iosToken = ""
                        feedback = "Credenziali cancellate. Interrompi anche la trasmissione di sistema."
                    }
                    if !feedback.isEmpty {
                        Text(feedback).font(.footnote)
                    }
                }
                Section("Avvia condivisione schermo") {
                    Text("Tocca il pulsante Apple, seleziona FF Remote Lab e conferma. iOS mostra un indicatore e consente di interrompere la trasmissione.")
                    HStack {
                        Spacer()
                        BroadcastPicker()
                            .frame(width: 70, height: 60)
                        Spacer()
                    }
                    Text("Prima di ogni trasmissione è necessaria la conferma sul telefono. Nessuna acquisizione nascosta.")
                        .font(.footnote)
                }
            }
            .navigationTitle("FF Remote Lab")
        }
    }
}

struct BroadcastPicker: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(
            frame: CGRect(x: 0, y: 0, width: 70, height: 60))
        picker.preferredExtension = "it.fflab.remoteios.broadcast"
        picker.showsMicrophoneButton = false
        return picker
    }
    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
