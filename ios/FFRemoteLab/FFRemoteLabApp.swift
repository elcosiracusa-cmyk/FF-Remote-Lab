import SwiftUI
import ReplayKit

@main
struct FFRemoteLabApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                Label("FF Remote Lab iOS", systemImage: "iphone.gen3")
                    .font(.title.bold())
                Text("Condivisione schermo solo con il consenso del proprietario.")
                    .font(.headline)
                Text("Tocca il selettore di sistema qui sotto per avviare una trasmissione ReplayKit. iOS mostra l'indicatore di registrazione e consente di interrompere la sessione.")
                BroadcastPicker()
                    .frame(width: 65, height: 55)
                    .accessibilityLabel("Avvia condivisione schermo autorizzata")
                Text("Versione sperimentale: questa estensione riceve i fotogrammi ma non li trasmette ancora a Kali. Nessun controllo remoto del touch e nessun accesso tramite ADB.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding()
            .navigationTitle("FF Remote Lab")
        }
    }
}

struct BroadcastPicker: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 65, height: 55))
        picker.preferredExtension = "it.fflab.remoteios.broadcast"
        picker.showsMicrophoneButton = false
        return picker
    }

    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
