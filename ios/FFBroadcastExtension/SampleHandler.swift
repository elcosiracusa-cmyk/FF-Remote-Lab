import ReplayKit
import CoreImage
import UIKit

/// An explicitly user-started ReplayKit broadcast upload extension.
/// Sends at most 1 JPEG/second using HTTPS to a private Tailscale relay.
/// Streaming stops with the system broadcast or when credentials are revoked.
final class SampleHandler: RPBroadcastSampleHandler {
    private let group = "group.it.fflab.remoteios"
    private let context = CIContext()
    private var lastSendTime: TimeInterval = 0
    private var uploading = false
    private let queue = DispatchQueue(label: "it.fflab.remoteios.frames")

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        lastSendTime = 0
        uploading = false
        guard let prefs = UserDefaults(suiteName: group),
              let address = prefs.string(forKey: "serverURL"),
              let token = prefs.string(forKey: "iosToken"),
              address.hasPrefix("https://"), address.hasSuffix("/"),
              token.count >= 32 else {
            finishBroadcastWithError(NSError(
                domain: "FFRemoteLab", code: 1,
                userInfo: [NSLocalizedDescriptionKey:
                    "Configura il server privato e il token nell'app prima di trasmettere."]))
            return
        }
    }

    override func broadcastFinished() {
        queue.sync { uploading = false }
    }

    override func broadcastPaused() {}

    override func broadcastResumed() {}

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer,
                                      with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .video else { return }
        let now = Date().timeIntervalSince1970
        guard now - lastSendTime >= 1 else { return }
        guard let prefs = UserDefaults(suiteName: group),
              let base = prefs.string(forKey: "serverURL"),
              let token = prefs.string(forKey: "iosToken"),
              base.hasPrefix("https://"), base.hasSuffix("/"),
              !base.contains("@"), token.count >= 32,
              let url = URL(string: base + "v1/ios/frame"),
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)
        else { return }

        // Keep frame encoding simple and downsize to save bandwidth and memory.
        let image = CIImage(cvPixelBuffer: pixelBuffer)
        let longest = max(image.extent.width, image.extent.height)
        let scale = min(CGFloat(1), CGFloat(720) / max(longest, CGFloat(1)))
        let reduced = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cgImage = context.createCGImage(reduced, from: reduced.extent),
              let jpeg = UIImage(cgImage: cgImage).jpegData(compressionQuality: 0.45),
              jpeg.count <= 450_000 else { return }

        let shouldSend = queue.sync { () -> Bool in
            if uploading { return false }
            uploading = true
            lastSendTime = now
            return true
        }
        guard shouldSend else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 8
        request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        URLSession.shared.uploadTask(with: request, from: jpeg) { [weak self] _, _, _ in
            self?.queue.async {
                self?.uploading = false
            }
        }.resume()
    }
}
